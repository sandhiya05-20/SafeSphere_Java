package com.safesphere.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBTest {
    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC"); // makes sure driver is loaded
            Connection conn = DriverManager.getConnection("jdbc:sqlite:data/test.db");
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO test (name) VALUES ('SafeSphere Works!')");
            System.out.println("Database connection successful ✅");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}