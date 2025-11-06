package com.safesphere.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtils {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256; // bits
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private PasswordUtils() {}

    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashPassword(char[] password, String base64Salt) {
        try {
            byte[] salt = Base64.getDecoder().decode(base64Salt);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new RuntimeException("Error hashing password", ex);
        }
    }

    public static boolean verifyPassword(char[] password, String expectedHashBase64, String base64Salt) {
        String computed = hashPassword(password, base64Salt);
        return constantTimeEquals(Base64.getDecoder().decode(computed), Base64.getDecoder().decode(expectedHashBase64));
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    // Legacy fallback: simple SHA-256 base64 (used only if your app previously stored unsalted SHA-256)
    public static String sha256Base64(char[] password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] digest = md.digest(bytes);
            Arrays.fill(bytes, (byte)0);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
}
