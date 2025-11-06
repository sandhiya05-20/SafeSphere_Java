package com.safesphere.data;

import java.sql.*;

/**
 * Small, idempotent migration helper for the 'users' table.
 *
 * - Checks PRAGMA table_info(users) and only runs ALTER TABLE if 'salt' is missing.
 * - If the database is busy/locked, retries a few times with small backoff.
 */
public final class DbMigration {
    private DbMigration() {}

    // Retry/backoff settings for ALTER TABLE when DB is busy
    private static final int MAX_RETRIES = 6;
    private static final int[] BACKOFF_MS = {100, 200, 300, 400, 600, 800};

    public static void ensureSaltColumn(Connection conn) throws SQLException {
        if (conn == null) throw new SQLException("Connection is null in DbMigration.ensureSaltColumn");

        boolean hasSalt = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(users);")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if ("salt".equalsIgnoreCase(name)) {
                    hasSalt = true;
                    break;
                }
            }
        }

        if (hasSalt) {
            // already present; nothing to do
            System.out.println("[DbMigration] 'salt' column already present.");
            return;
        }

        // Attempt to add column with retries if DB is busy/locked
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("ALTER TABLE users ADD COLUMN salt TEXT;");
                System.out.println("[DbMigration] Added 'salt' column to users table.");
                return; // success
            } catch (SQLException ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                // If the column was added concurrently by another process/thread, exit cleanly.
                if (msg.contains("duplicate column name") || msg.contains("already exists") || msg.contains("duplicate column")) {
                    System.out.println("[DbMigration] 'salt' column already exists (concurrent).");
                    return;
                }

                // If DB is busy/locked, retry after a small backoff
                if (msg.contains("busy") || msg.contains("locked")) {
                    if (attempt < BACKOFF_MS.length) {
                        int wait = BACKOFF_MS[attempt];
                        System.err.println("[DbMigration] DB busy/locked on ALTER TABLE (attempt " + (attempt + 1) + "). Retrying in " + wait + "ms");
                        try {
                            Thread.sleep(wait);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Interrupted while waiting to retry DbMigration", ie);
                        }
                        attempt++;
                        continue;
                    } else {
                        throw new SQLException("Failed to add 'salt' column after retries: " + ex.getMessage(), ex);
                    }
                }

                // Other SQL errors should be propagated so caller can decide (init should fail loudly)
                throw ex;
            }
        }
    }
}

