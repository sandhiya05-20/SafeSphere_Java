package com.safesphere.data;

import com.safesphere.security.EncryptionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DataStorageDB: stores encrypted entries in the DB.
 * It uses the existing EncryptionManager which produces/consumes Base64 strings.
 *
 * - saveEntry(ownerId, type, title, plaintext) : encrypts plaintext and stores as BLOB.
 * - loadEntries(ownerId) : returns list of EntryItem with decrypted 'content' field.
 *
 * This version calls DbMigration.ensureSaltColumn(...) once at construction time so the
 * users table will gain the 'salt' column if missing.
 */
public class DataStorageDB {
    private final DBManager db;

    public DataStorageDB() throws Exception {
        db = DBManager.getInstance();

        // Run DB migration once at startup to ensure users.salt column exists.
        // We open a short-lived connection, run the migration, then close it.
        try (Connection conn = db.getConnection()) {
            if (conn == null) {
                System.err.println("[DataStorageDB] Warning: got null Connection from DBManager");
            } else {
                try {
                    DbMigration.ensureSaltColumn(conn);
                } catch (SQLException ex) {
                    // Bubble up as Exception to caller so startup fails loudly if migration cannot run.
                    System.err.println("[DataStorageDB] DbMigration failed: " + ex.getMessage());
                    throw ex;
                }
            }
        }
    }

    /**
     * Save a plaintext entry: encrypts using EncryptionManager and stores as BLOB.
     */
    public void saveEntry(int ownerId, String type, String title, String plaintext) throws Exception {
        String encryptedBase64 = EncryptionManager.encrypt(plaintext);
        byte[] blob = encryptedBase64.getBytes("UTF-8"); // store Base64 string as bytes
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO entries(owner_id, type, title, encrypted_blob) VALUES(?,?,?,?)")) {
            ps.setInt(1, ownerId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setBytes(4, blob);
            ps.executeUpdate();
        }
    }

    /**
     * Load entries for a user and return decrypted content in EntryItem.content.
     */
    public List<EntryItem> loadEntries(int ownerId) throws Exception {
        List<EntryItem> out = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, type, title, encrypted_blob, modified_at FROM entries WHERE owner_id = ? ORDER BY modified_at DESC")) {
            ps.setInt(1, ownerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                EntryItem it = new EntryItem();
                it.id = rs.getInt("id");
                it.type = rs.getString("type");
                it.title = rs.getString("title");
                it.modifiedAt = rs.getString("modified_at");

                byte[] blob = rs.getBytes("encrypted_blob");
                if (blob != null && blob.length > 0) {
                    String encryptedBase64 = new String(blob, "UTF-8");
                    String plaintext = EncryptionManager.decrypt(encryptedBase64);
                    it.content = plaintext;
                } else {
                    it.content = "";
                }
                out.add(it);
            }
        }
        return out;
    }

    /**
     * Optional: delete an entry by id
     */
    public void deleteEntry(int entryId) throws Exception {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM entries WHERE id = ?")) {
            ps.setInt(1, entryId);
            ps.executeUpdate();
        }
    }

    /**
     * Update an existing entry's title and plaintext content.
     */
    public void updateEntry(int entryId, String newTitle, String newPlaintext) throws Exception {
        String encryptedBase64 = EncryptionManager.encrypt(newPlaintext);
        byte[] blob = encryptedBase64.getBytes("UTF-8");

        String sql = "UPDATE entries SET title = ?, encrypted_blob = ?, modified_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newTitle);
            ps.setBytes(2, blob);
            ps.setInt(3, entryId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                System.out.println("updateEntry: no row with id=" + entryId);
            }
        }
    }

    /** Simple holder for an entry (decrypted content included) */
    public static class EntryItem {
        public int id;
        public String type;
        public String title;
        public String content;       // decrypted plaintext
        public String modifiedAt;
    }

    /**
     * Demo method — verifies database connection and basic save/load.
     */
    public static void demoMain() throws Exception {
        System.out.println("Running DataStorageDB demo...");

        // Create instance (uses DBManager)
        DataStorageDB db = new DataStorageDB();

        // Just to test connection
        try (Connection c = db.db.getConnection()) {
            if (c != null) {
                System.out.println("✅ Database connection successful!");
            } else {
                System.out.println("❌ Database connection failed!");
            }
        }

        // Simple test: try saving an entry
        System.out.println("Inserting test entry...");
        db.saveEntry(1, "Note", "Hello Entry", "This is a test message!");
        System.out.println("Entry saved!");

        // Now load all entries for user 1
        System.out.println("Loading entries...");
        List<EntryItem> entries = db.loadEntries(1);
        for (EntryItem e : entries) {
            System.out.println("ID: " + e.id + " | Title: " + e.title + " | Content: " + e.content);
        }

        System.out.println("Demo completed successfully ✅");
    }
}
