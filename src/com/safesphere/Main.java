package com.safesphere;

import com.safesphere.data.DBManager;
import com.safesphere.security.KeyDerivation;
import com.safesphere.data.DefaultUserHelper;
import com.safesphere.ui.LoginScreen;

import javax.swing.SwingUtilities;

/**
 * Main entry point for SafeSphere.
 * Initializes the database, encryption salt, and default user,
 * then opens the Login screen.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Starting SafeSphere...");

        try {
            // Initialize database connection and essential components
            DBManager.getInstance();
            KeyDerivation.loadOrCreateSalt();
            DefaultUserHelper.createDefaultUserIfMissing();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Database initialization failed!");
            return;
        }

        // Launch the LoginScreen on the Swing event thread
        SwingUtilities.invokeLater(() -> new LoginScreen().setVisible(true));
    }
}