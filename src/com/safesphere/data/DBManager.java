package com.safesphere.data;

import java.nio.file.*;
import java.sql.*;

public class DBManager {
    private static final String DB_PATH = "data/safesphere.db";
    private static final String URL = "jdbc:sqlite:" + DB_PATH;
    private static DBManager instance;

    private DBManager() throws Exception {
        // ensure data folder exists
        Path p = Paths.get("data");
        if (!Files.exists(p)) Files.createDirectories(p);

        // create DB file and schema (driver creates file on connect)
        try (Connection c = getConnection()) {
            initSchema(c);
        }
    }

    // singleton accessor
    public static synchronized DBManager getInstance() throws Exception {
        if (instance == null) instance = new DBManager();
        return instance;
    }

    // get a new connection
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // create the tables if they do not exist
    private void initSchema(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("""
            CREATE TABLE IF NOT EXISTS users (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              username TEXT UNIQUE NOT NULL,
              pin_hash TEXT NOT NULL,
              salt BLOB NOT NULL
            );
            """);

            s.execute("""
            CREATE TABLE IF NOT EXISTS entries (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_id INTEGER NOT NULL,
              type TEXT,
              title TEXT,
              encrypted_blob BLOB,
              modified_at DATETIME DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY(owner_id) REFERENCES users(id)
            );
            """);

            s.execute("""
            CREATE TABLE IF NOT EXISTS behavior_samples (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner_id INTEGER NOT NULL,
              sample_csv TEXT,
              created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY(owner_id) REFERENCES users(id)
            );
            """);
        }
    }
}
