package com.safesphere.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Small utility to print the events table to the console.
 * Run this after you've exercised the UI (login, add/edit/delete) to verify events were logged.
 */
public class EventViewer {
    public static void main(String[] args) throws Exception {
        // ensure DBManager initialized (creates DB/tables if missing)
        DBManager.getInstance();

        System.out.println("=== events table ===");
        try (Connection c = DBManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, owner_id, event_type, event_meta, created_at FROM events ORDER BY created_at DESC LIMIT 100")) {
            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf(
                        "%d | owner_id=%s | %s | meta=%s | %s%n",
                        rs.getInt("id"),
                        (rs.getObject("owner_id") == null ? "NULL" : rs.getInt("owner_id")),
                        rs.getString("event_type"),
                        rs.getString("event_meta"),
                        rs.getString("created_at")
                );
            }
            if (count == 0) {
                System.out.println("(no rows found in events table)");
            } else {
                System.out.println("Total printed: " + count);
            }
        }
    }
}