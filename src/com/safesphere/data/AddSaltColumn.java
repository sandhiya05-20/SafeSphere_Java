package com.safesphere.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * One-time utility to add 'salt' column to users table.
 */
public class AddSaltColumn {
    public static void main(String[] args) {
        String path = "E:\\APP\\SafeSphere_Java\\data\\safesphere.db";
        String url = "jdbc:sqlite:" + path;
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement()) {

            System.out.println("Adding 'salt' column if not exists...");
            st.executeUpdate("ALTER TABLE users ADD COLUMN salt TEXT;");
            System.out.println("✅ Column 'salt' added successfully!");

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate column name")) {
                System.out.println("Column already exists — nothing to do.");
            } else {
                e.printStackTrace();
            }
        }
    }
}
