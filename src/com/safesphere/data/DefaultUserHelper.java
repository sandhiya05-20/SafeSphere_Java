package com.safesphere.data;

import java.sql.*;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Tiny helper for demo/testing:
 * - If users table is empty, creates a default user:
 *     username = "owner", PIN = "1234"
 * - PIN is stored as base64(SHA-256(pin)) to match PinManager.verifyPinForUser earlier.
 *
 * Remove this helper or change for production/real registration.
 */
public class DefaultUserHelper {

    public static void createDefaultUserIfMissing() throws Exception {
        DBManager db = DBManager.getInstance();
        try (Connection c = db.getConnection()) {
            // ensure users table exists (DBManager already created schema)
            try (Statement s = c.createStatement()) {
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users");
                int count = rs.next() ? rs.getInt(1) : 0;
                if (count == 0) {
                    // read salt file if present (KeyDerivation created it)
                    byte[] salt;
                    try {
                        salt = java.nio.file.Files.readAllBytes(Paths.get("data/salt.bin"));
                    } catch (Exception ex) {
                        // if no salt found, create small fallback salt
                        salt = "defaultsalt123456".getBytes("UTF-8");
                    }

                    String pin = "1234";
                    String hash = sha256Base64(pin);

                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO users(username, pin_hash, salt) VALUES(?,?,?)")) {
                        ps.setString(1, "owner");
                        ps.setString(2, hash);
                        ps.setBytes(3, salt);
                        ps.executeUpdate();
                    }

                    System.out.println("Created default user 'owner' with PIN 1234 for testing.");
                } else {
                    // optional: print existing user count
                    System.out.println("Users already exist in DB: " + count);
                }
            }
        }
    }

    private static String sha256Base64(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] b = md.digest(input.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(b);
    }
}