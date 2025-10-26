package com.safesphere.security;

import com.safesphere.data.DBManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class PinManager {
    private static final String PIN_FILE = "data/pin.cfg";

    // Save hashed PIN (creates default if none)
    public static void savePin(String pin) throws Exception {
        byte[] hash = hashPin(pin);
        String encoded = Base64.getEncoder().encodeToString(hash);
        File f = new File(PIN_FILE);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(Paths.get(PIN_FILE), encoded.getBytes());
    }

    // Verify entered PIN against stored hashed PIN (creates default 1234 if missing)
    public static boolean verifyPin(String entered) throws Exception {
        File file = new File(PIN_FILE);
        if (!file.exists()) {
            savePin("1234"); // default for first run
        }
        String stored = new String(Files.readAllBytes(Paths.get(PIN_FILE)));
        byte[] storedHash = Base64.getDecoder().decode(stored);
        byte[] enteredHash = hashPin(entered);
        return MessageDigest.isEqual(storedHash, enteredHash);
    }

    public static boolean verifyPinForUser(String username, String enteredPin) throws Exception {
        DBManager db = DBManager.getInstance();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT pin_hash FROM users WHERE username = ?")) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                System.out.println("No such user in database: " + username);
                return false;
            }

            String storedHash = rs.getString("pin_hash");
            String enteredHash = sha256Base64(enteredPin);
            boolean match = storedHash.equals(enteredHash);

            if (match)
                System.out.println("✅ PIN verified successfully for user: " + username);
            else
                System.out.println("❌ Incorrect PIN for user: " + username);

            return match;
        }
    }

    // helper for DB PIN verification
    private static String sha256Base64(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static byte[] hashPin(String pin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(pin.getBytes("UTF-8"));
    }
    // 🔹 Get user ID from the users table by username
    public static int getUserIdForUsername(String username) throws Exception {
        DBManager db = DBManager.getInstance();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        // fallback if not found
        return 1;
    }
}