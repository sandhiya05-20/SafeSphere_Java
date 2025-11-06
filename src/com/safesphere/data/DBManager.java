package com.safesphere.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * DBManager - simple singleton wrapper around a SQLite connection.
 *
 * Responsibilities:
 *  - Ensure the data folder and DB file exist
 *  - Create required tables if missing (users, entries, events)
 *  - Provide connections via getConnection()
 *
 * This version also ensures the 'salt' column exists by calling DbMigration.ensureSaltColumn(...)
 * during initialization and whenever a new connection is opened.
 */
public final class DBManager {
    private static final String DATA_DIR = "data";
    private static final String DB_FILE = "safesphere.db";
    private static final String JDBC_URL = "jdbc:sqlite:" + DATA_DIR + File.separator + DB_FILE;

    private static volatile DBManager instance;

    private DBManager() throws Exception {
        ensureDataDirectory();
        initializeDatabase();
    }

    /**
     * Get singleton instance (creates DB file and tables on first call).
     */
    public static DBManager getInstance() throws Exception {
        if (instance == null) {
            synchronized (DBManager.class) {
                if (instance == null) {
                    instance = new DBManager();
                }
            }
        }
        return instance;
    }

    /**
     * Return a new JDBC Connection to the SQLite database.
     * Caller should close the connection when done.
     *
     * This method also attempts to run the schema migration (ensure 'salt' column)
     * right after opening the connection so any runtime DB recreation will be
     * patched automatically.
     */
    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(JDBC_URL);

        // Make SQLite wait up to 5 seconds if the database is busy
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            System.err.println("Could not set busy_timeout: " + e.getMessage());
        }

        // Ensure foreign keys are enforced
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException ignored) {
            // ignore if pragma fails for some reason
        }

        // Ensure schema migration (idempotent) so runtime DB recreation won't break code.
        try {
            DbMigration.ensureSaltColumn(c);
        } catch (SQLException ex) {
            // Don't let migration failures here crash the caller - log and continue.
            System.err.println("[DBManager] Warning: DbMigration.ensureSaltColumn failed on getConnection(): " + ex.getMessage());
        }

        return c;
    }

    // ---------- Internal helpers ----------

    private void ensureDataDirectory() throws Exception {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok && !dir.exists()) {
                throw new Exception("Could not create data directory: " + DATA_DIR);
            }
        }
    }

    /**
     * Create tables if they do not exist.
     * This method opens a short-lived connection and executes CREATE TABLE statements.
     * After creating tables it calls DbMigration.ensureSaltColumn(...) to make sure
     * the users table has the 'salt' column.
     */
    private void initializeDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = c.createStatement()) {
                // ensure foreign keys enforcement
                stmt.execute("PRAGMA foreign_keys = ON;");

                // Users table - stores username and pin_hash (used by PinManager)
                // Note: new DBs will include 'salt' column here; existing DBs will be patched by DbMigration.
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS users (" +
                                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "  username TEXT NOT NULL UNIQUE," +
                                "  pin_hash TEXT," +
                                "  salt TEXT" +
                                ");"
                );

                // Entries table - stores encrypted entries
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS entries (" +
                                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "  owner_id INTEGER NOT NULL," +
                                "  type TEXT," +
                                "  title TEXT," +
                                "  encrypted_blob BLOB," +
                                "  modified_at TEXT DEFAULT (datetime('now'))," +
                                "  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE" +
                                ");"
                );

                // Events table - for behavior analysis / logging
                stmt.execute(
                        "CREATE TABLE IF NOT EXISTS events (" +
                                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "  owner_id INTEGER," +                     // nullable for unknown/failed login
                                "  event_type TEXT NOT NULL," +             // e.g., LOGIN_ATTEMPT, LOGIN_SUCCESS, ADD_ENTRY
                                "  event_meta TEXT," +                      // optional JSON/text metadata
                                "  created_at TEXT DEFAULT (datetime('now'))," +
                                "  FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE SET NULL" +
                                ");"
                );
            }

            // Run the column migration (idempotent) while we still have this connection.
            try {
                DbMigration.ensureSaltColumn(c);
            } catch (SQLException ex) {
                // Bubble up as a descriptive exception so init fails loudly if migration cannot run.
                throw new Exception("Failed to run DbMigration.ensureSaltColumn: " + ex.getMessage(), ex);
            }
        } catch (SQLException ex) {
            throw new Exception("Failed to initialize database: " + ex.getMessage(), ex);
        }
    }

    // ---------- Convenience methods (optional) ----------

    /**
     * Check if a table exists (not used by startup but handy for debugging).
     */
    public boolean tableExists(String tableName) {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }
}
