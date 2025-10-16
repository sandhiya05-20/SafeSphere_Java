package com.safesphere.security;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
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

    private static byte[] hashPin(String pin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(pin.getBytes("UTF-8"));
    }
}

