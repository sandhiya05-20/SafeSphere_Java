package com.safesphere.security;

import com.safesphere.data.DBManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalTime;

/**
 * BehaviorAnalyzer - lightweight rule-based checks for suspicious activity.
 *
 * Returns a human-readable alert string when a check triggers, or null when OK.
 *
 * Rules implemented:
 *  - checkFailedLoginBurst: N or more LOGIN_FAILED events in the last M minutes
 *  - checkUnusualLoginHour: login hour differs significantly from the user's most common login hour
 *  - checkRapidEdits: many EDIT_ENTRY/DELETE_ENTRY events in a short window
 *
 * Use:
 *   String alert = BehaviorAnalyzer.runBasicChecksForLogin(ownerId);
 *   if (alert != null) show alert to user.
 */
public final class BehaviorAnalyzer {
    private BehaviorAnalyzer() {}

    /**
     * Check for burst of failed logins for an owner (ownerId may be null if unknown).
     * @param ownerId owner id (nullable)
     * @param threshold number of failed attempts to trigger
     * @param minutesWindow lookback window in minutes
     * @return alert string or null
     */
    public static String checkFailedLoginBurst(Integer ownerId, int threshold, int minutesWindow) {
        String sql;
        if (ownerId == null) {
            // For unknown owner, check all failed attempts (useful for brute-force detection)
            sql = "SELECT COUNT(*) AS cnt FROM events WHERE event_type = 'LOGIN_FAILED' " +
                    "AND datetime(created_at) >= datetime('now', ?)";
        } else {
            sql = "SELECT COUNT(*) AS cnt FROM events WHERE owner_id = ? AND event_type = 'LOGIN_FAILED' " +
                    "AND datetime(created_at) >= datetime('now', ?)";
        }

        try (Connection c = DBManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (ownerId == null) {
                ps.setString(1, String.format("-%d minutes", minutesWindow));
            } else {
                ps.setInt(1, ownerId);
                ps.setString(2, String.format("-%d minutes", minutesWindow));
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    if (cnt >= threshold) {
                        return "Multiple failed login attempts detected (" + cnt + " in last " + minutesWindow + " minutes).";
                    }
                }
            }
        } catch (Exception ex) {
            // Swallow but print for dev visibility
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Check whether current login hour is unusual compared to historical most-active login hour.
     * @param ownerId owner id (should be non-null)
     * @param loginHour hour of day 0-23
     * @return alert string or null
     */
    public static String checkUnusualLoginHour(int ownerId, int loginHour) {
        String sql = "SELECT STRFTIME('%H', created_at) AS hour, COUNT(*) AS cnt " +
                "FROM events WHERE owner_id = ? AND event_type = 'LOGIN_SUCCESS' " +
                "GROUP BY hour ORDER BY cnt DESC LIMIT 1";
        try (Connection c = DBManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String topHourStr = rs.getString("hour");
                    if (topHourStr != null && !topHourStr.isEmpty()) {
                        int topHour = Integer.parseInt(topHourStr);
                        int diff = Math.abs(topHour - loginHour);
                        if (diff >= 6) { // arbitrary threshold: 6+ hours difference considered unusual
                            return "Login at unusual hour (" + loginHour + ":00). Most logins occur around " + topHour + ":00.";
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Check for many edits/deletes in a short time window.
     * @param ownerId owner id (nullable)
     * @param threshold number of edit/delete events to trigger
     * @param minutesWindow lookback minutes
     * @return alert string or null
     */
    public static String checkRapidEdits(Integer ownerId, int threshold, int minutesWindow) {
        String sql;
        if (ownerId == null) {
            sql = "SELECT COUNT(*) AS cnt FROM events WHERE event_type IN ('EDIT_ENTRY','DELETE_ENTRY') " +
                    "AND datetime(created_at) >= datetime('now', ?)";
        } else {
            sql = "SELECT COUNT(*) AS cnt FROM events WHERE owner_id = ? AND event_type IN ('EDIT_ENTRY','DELETE_ENTRY') " +
                    "AND datetime(created_at) >= datetime('now', ?)";
        }

        try (Connection c = DBManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (ownerId == null) {
                ps.setString(1, String.format("-%d minutes", minutesWindow));
            } else {
                ps.setInt(1, ownerId);
                ps.setString(2, String.format("-%d minutes", minutesWindow));
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    if (cnt >= threshold) {
                        return "High rate of edits/deletes detected (" + cnt + " in last " + minutesWindow + " minutes).";
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // ---------- Convenience combined checks ----------

    /**
     * Run a small set of basic checks after a login event.
     * @param ownerId owner id (nullable)
     * @return first alert string found or null
     */
    public static String runBasicChecksForLogin(Integer ownerId) {
        // 1) failed login burst: 5 fails in 10 minutes
        String s = checkFailedLoginBurst(ownerId, 5, 10);
        if (s != null) return s;

        // 2) unusual login hour (only if ownerId known)
        if (ownerId != null) {
            int currentHour = LocalTime.now().getHour();
            s = checkUnusualLoginHour(ownerId, currentHour);
            if (s != null) return s;
        }

        return null;
    }

    /**
     * Run basic checks after activity (add/edit/delete).
     * @param ownerId owner id (nullable)
     * @return first alert string found or null
     */
    public static String runBasicChecksForActivity(Integer ownerId) {
        // rapid edits: 10 edits in 5 minutes
        String s = checkRapidEdits(ownerId, 10, 5);
        if (s != null) return s;

        return null;
    }
}