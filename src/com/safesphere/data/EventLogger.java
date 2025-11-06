package com.safesphere.data;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * EventLogger - utility class to record user/system actions into the 'events' table.
 * Use this to log things like login attempts, entry saves, deletions, etc.
 */
public class EventLogger {

    /**
     * Log an event with optional metadata (like "username=owner" or "title=Bank Entry").
     *
     * @param ownerId   The user ID (can be null or 0 if not logged in yet)
     * @param eventType The type of event, e.g. "LOGIN_SUCCESS", "LOGIN_FAIL", "ADD_ENTRY"
     * @param meta      Optional text/JSON metadata
     */
    public static void log(Integer ownerId, String eventType, String meta) {
        try (Connection c = DBManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO events(owner_id, event_type, event_meta) VALUES(?,?,?)")) {

            if (ownerId == null || ownerId == 0)
                ps.setNull(1, java.sql.Types.INTEGER);
            else
                ps.setInt(1, ownerId);

            ps.setString(2, eventType);
            ps.setString(3, meta);
            ps.executeUpdate();

            System.out.println("🪶 Event logged: " + eventType + " | " + (meta != null ? meta : "(no meta)"));

        } catch (Exception e) {
            System.err.println("⚠️ Failed to log event: " + e.getMessage());
        }
    }
}