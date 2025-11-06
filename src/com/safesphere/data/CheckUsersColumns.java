package com.safesphere.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Quick test program to list the columns in the 'users' table.
 * Run this once to verify whether the 'salt' column exists.
 */
public class CheckUsersColumns {
    public static void main(String[] args) throws Exception {
        // Path to your existing DB file
        String path = "E:\\APP\\SafeSphere_Java\\data\\safesphere.db";
        String url = "jdbc:sqlite:" + path;

        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(users);")) {

            System.out.println("Columns in 'users' table:");
            while (rs.next()) {
                int cid = rs.getInt("cid");
                String name = rs.getString("name");
                String type = rs.getString("type");
                System.out.println(cid + " | " + name + " | " + type);
            }
        }
    }
}

