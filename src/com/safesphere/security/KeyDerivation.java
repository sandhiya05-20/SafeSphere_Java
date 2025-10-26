package com.safesphere.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;

/**
 * Simple PBKDF2-based key derivation for demo:
 * - deriveKey(pin) returns an AES SecretKeySpec
 * - salt is stored in data/salt.bin (created automatically)
 *
 * Note: For production you'd store per-user salt in DB. This is a simple
 * prototype that works for the project demo.
 */
public class KeyDerivation {
    public static final int ITERATIONS = 100_000;
    public static final int KEY_LENGTH = 128; // bits for AES-128
    private static final String SALT_FILE = "data/salt.bin";

    public static byte[] generateSalt() throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[16];
        rnd.nextBytes(salt);
        Files.write(Paths.get(SALT_FILE), salt);
        return salt;
    }

    public static byte[] loadOrCreateSalt() throws Exception {
        File f = new File(SALT_FILE);
        if (!f.exists()) return generateSalt();
        return Files.readAllBytes(Paths.get(SALT_FILE));
    }

    public static SecretKeySpec deriveKey(String pin) throws Exception {
        byte[] salt = loadOrCreateSalt();
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        SecretKey key = skf.generateSecret(spec);
        byte[] keyBytes = key.getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
