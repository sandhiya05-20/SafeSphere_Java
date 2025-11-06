package com.safesphere.security;

import com.safesphere.data.DBManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * PinManager - handles saving/verifying a local fallback PIN file and
 * DB-backed PIN verification for a user. Also provides async lock persistence
 * so UI doesn't block while trying to update DB when SQLite is busy.
 *
 * Updated to support salted password storage (via PasswordUtils) while keeping
 * backward compatibility with legacy SHA-256(base64) stored PINs. Legacy users
 * are migrated to salted PBKDF2 on successful login.
 */
public class PinManager {
    private static final String PIN_FILE = "data/pin.cfg";

    // Async lock writer: single background thread used to persist lock updates.
    private static final LockPersistence LOCK_PERSIST = new LockPersistence();

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

    /**
     * Verify PIN for a database user (username). Supports:
     *  - salted PBKDF2 hashes (new flow): users.pin_hash + users.salt
     *  - legacy SHA-256(base64) hashes (old flow): users.pin_hash with no salt
     *
     * On successful legacy verification, this method migrates the user to a
     * salted PBKDF2 hash and stores the salt in the users table.
     *
     * Returns true if authentication succeeds.
     */
    public static boolean verifyPinForUser(String username, String enteredPin) throws Exception {
        DBManager db = DBManager.getInstance();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, pin_hash, salt FROM users WHERE username = ?")) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("No such user in database: " + username);
                    return false;
                }

                int userId = rs.getInt("id");
                String storedHash = rs.getString("pin_hash");
                String salt = rs.getString("salt"); // may be null for legacy users

                if (storedHash == null) {
                    System.out.println("User has no stored hash: " + username);
                    return false;
                }

                // Convert entered PIN to char[] for PasswordUtils
                char[] enteredChars = enteredPin != null ? enteredPin.toCharArray() : new char[0];
                try {
                    // If salt exists -> new flow (salted PBKDF2)
                    if (salt != null && !salt.trim().isEmpty()) {
                        boolean ok = PasswordUtils.verifyPassword(enteredChars, storedHash, salt);
                        if (ok) {
                            System.out.println("✅ PIN verified (salted) for user: " + username);
                            return true;
                        } else {
                            System.out.println("❌ Incorrect PIN (salted) for user: " + username);
                            return false;
                        }
                    } else {
                        // Legacy flow: storedHash is SHA-256(base64)
                        String legacyHash = PasswordUtils.sha256Base64(enteredChars);
                        if (legacyHash.equals(storedHash)) {
                            // Migrate to salted PBKDF2
                            String newSalt = PasswordUtils.generateSalt();
                            String newHash = PasswordUtils.hashPassword(enteredChars, newSalt);

                            // Update DB with new hash and salt
                            try (PreparedStatement up = c.prepareStatement("UPDATE users SET pin_hash = ?, salt = ? WHERE id = ?")) {
                                up.setString(1, newHash);
                                up.setString(2, newSalt);
                                up.setInt(3, userId);
                                up.executeUpdate();
                            } catch (SQLException upEx) {
                                // Log but still treat login as successful (migration failure shouldn't lock user out)
                                System.err.println("Failed to migrate user to salted hash for user " + username + ": " + upEx.getMessage());
                            }

                            System.out.println("✅ PIN verified (legacy) and migrated to salted hash for user: " + username);
                            return true;
                        } else {
                            System.out.println("❌ Incorrect PIN (legacy) for user: " + username);
                            return false;
                        }
                    }
                } finally {
                    // zero the entered chars for safety
                    for (int i = 0; i < enteredChars.length; i++) enteredChars[i] = '\0';
                }
            }
        }
    }

    // Attempt to return the DB user id for a username. Returns -1 if not found or error.
    public static int getUserIdForUsername(String username) {
        try {
            DBManager db = DBManager.getInstance();
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id");
            }
        } catch (Exception e) {
            // ignore: caller handles missing helper / fallbacks
        }
        return -1;
    }

    /**
     * Non-blocking: schedule an async write to set locked_until for a user.
     * The actual DB update will be retried in background if SQLite is busy.
     *
     * minutesToLock: number of minutes from now the account should be locked.
     * If minutesToLock <= 0, this will clear the locked_until.
     */
    public static void setAccountLockAsync(int ownerId, int minutesToLock) {
        if (ownerId <= 0) return;
        LOCK_PERSIST.scheduleLock(ownerId, minutesToLock);
    }

    /**
     * Synchronous check whether account is locked (returns true if locked).
     * This is synchronous because callers sometimes need to read current state.
     * If DB error occurs we return false (treat as not locked) so callers don't block.
     */
    public static boolean isAccountLocked(int ownerId) {
        if (ownerId <= 0) return false;
        try {
            DBManager db = DBManager.getInstance();
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT locked_until FROM users WHERE id = ?")) {
                ps.setInt(1, ownerId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return false;
                String lockedUntil = rs.getString("locked_until"); // expects sqlite DATETIME string or null
                if (lockedUntil == null || lockedUntil.trim().isEmpty()) return false;
                // Use SQLite's datetime parsing: compare with 'now'
                try (PreparedStatement cmp = c.prepareStatement("SELECT (datetime('now') < ?) AS is_locked")) {
                    cmp.setString(1, lockedUntil);
                    ResultSet r2 = cmp.executeQuery();
                    if (r2.next()) return r2.getInt("is_locked") == 1;
                } catch (Exception ignore) {
                    // If comparison fails, fallback to not locked
                    return false;
                }
            }
        } catch (Exception e) {
            // If DB unavailable, don't assume locked (avoid locking out owner accidentally)
            System.err.println("Error checking account lock: " + e.getMessage());
        }
        return false;
    }

    // helper for DB PIN verification: SHA-256 + Base64 (legacy)
    private static String sha256Base64(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static byte[] hashPin(String pin) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(pin.getBytes("UTF-8"));
    }

    // --- Shutdown helper so background thread can be terminated on app exit if needed ---
    public static void shutdownBackgroundWorkers() {
        LOCK_PERSIST.shutdown();
    }
}

/**
 * Background helper that persists lock updates to the DB on a separate thread
 * and retries with small backoff if the DB returns busy errors.
 *
 * This class is package-private and intentionally non-public so it can live in the same file.
 */
class LockPersistence {
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LockPersistenceThread");
        t.setDaemon(true);
        return t;
    });

    // schedule a lock update (non-blocking)
    public void scheduleLock(int ownerId, int minutesToLock) {
        // create immutable snapshot of parameters for the runnable
        exec.submit(new LockTask(ownerId, minutesToLock));
    }

    public void shutdown() {
        try {
            exec.shutdown();
            exec.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            exec.shutdownNow();
        }
    }

    // Task that performs the DB update with retries/backoff.
    private static class LockTask implements Runnable {
        private final int ownerId;
        private final int minutesToLock;
        // retries/backoff pattern similar to what you were seeing in logs
        private static final int[] BACKOFF_MS = {200, 400, 600, 800, 1000, 1200};

        LockTask(int ownerId, int minutesToLock) {
            this.ownerId = ownerId;
            this.minutesToLock = minutesToLock;
        }

        @Override
        public void run() {
            try {
                DBManager db = DBManager.getInstance();
                boolean success = false;

                // Build SQL - using SQLite datetime() helper so we don't need to compute millis.
                // If minutesToLock <= 0 we'll clear locked_until (set NULL).
                String sqlSet = "UPDATE users SET locked_until = datetime('now', ?) WHERE id = ?";
                String sqlClear = "UPDATE users SET locked_until = NULL WHERE id = ?";

                int attempt = 0;
                while (attempt <= BACKOFF_MS.length && !success) {
                    try (Connection c = db.getConnection()) {
                        if (minutesToLock > 0) {
                            try (PreparedStatement ps = c.prepareStatement(sqlSet)) {
                                ps.setString(1, "+" + minutesToLock + " minutes");
                                ps.setInt(2, ownerId);
                                ps.executeUpdate();
                            }
                        } else {
                            try (PreparedStatement ps = c.prepareStatement(sqlClear)) {
                                ps.setInt(1, ownerId);
                                ps.executeUpdate();
                            }
                        }
                        success = true;
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                        if (msg.toLowerCase().contains("busy") || msg.toLowerCase().contains("locked")) {
                            // DB is busy, back off and retry
                            if (attempt < BACKOFF_MS.length) {
                                int wait = BACKOFF_MS[attempt];
                                System.err.println("DB busy when setting lock (attempt " + (attempt + 1) + "). Retrying after " + wait + "ms");
                                try {
                                    Thread.sleep(wait);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                attempt++;
                                continue;
                            } else {
                                System.err.println("Failed to set locked_until for user " + ownerId + " after " + (attempt + 1) + " retries.");
                                break;
                            }
                        } else {
                            // other SQL error (schema mismatch etc.) -- log and abort
                            System.err.println("Error setting locked_until for user " + ownerId + ": " + msg);
                            break;
                        }
                    }
                }
                if (!success) {
                    // best-effort failed; nothing else we can do here silently
                }
            } catch (Exception outer) {
                System.err.println("LockTask unexpected error: " + outer.getMessage());
            }
        }
    }
}
