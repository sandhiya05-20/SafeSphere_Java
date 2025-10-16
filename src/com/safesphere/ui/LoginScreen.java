package com.safesphere.ui;

import com.safesphere.security.PinManager;

import javax.swing.*;
import java.awt.*;

public class LoginScreen extends JFrame {
    private final JPasswordField pinField;

    public LoginScreen() {
        setTitle("SafeSphere - Secure Login");
        setSize(420, 280);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(null); // absolute positioning for neat UI

        // 🔹 Logo label
        ImageIcon icon = new ImageIcon("lock.png");
        Image img = icon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
        JLabel logo = new JLabel(" SafeSphere", new ImageIcon(img), SwingConstants.CENTER);
        logo.setFont(new Font("SansSerif", Font.BOLD, 26));
        logo.setBounds(80, 20, 250, 60);
        add(logo);

// Set window icon (top-left corner)
        setIconImage(img);

        // 🔹 Tagline
        JLabel tagline = new JLabel("Your Personal Data Protector", SwingConstants.CENTER);
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tagline.setBounds(80, 60, 250, 20);
        add(tagline);

        // 🔹 PIN field
        JLabel pinLabel = new JLabel("Enter PIN:");
        pinLabel.setBounds(100, 100, 80, 30);
        add(pinLabel);

        pinField = new JPasswordField();
        pinField.setEchoChar('●');
        pinField.setBounds(180, 100, 120, 30);
        add(pinField);

        // 🔹 Buttons
        JButton loginBtn = new JButton("Login");
        JButton changePinBtn = new JButton("Change PIN");
        JButton exitBtn = new JButton("Exit");

        loginBtn.setBounds(60, 160, 100, 35);
        changePinBtn.setBounds(170, 160, 120, 35);
        exitBtn.setBounds(300, 160, 60, 35);

        add(loginBtn);
        add(changePinBtn);
        add(exitBtn);

        // 🔹 Footer
        JLabel footer = new JLabel("© 2025 SafeSphere Labs", SwingConstants.CENTER);
        footer.setFont(new Font("SansSerif", Font.ITALIC, 11));
        footer.setBounds(100, 210, 220, 20);
        footer.setForeground(Color.GRAY);
        add(footer);

        // 🔹 Colors and background
        getContentPane().setBackground(new Color(240, 245, 250));
        getRootPane().setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, new Color(180, 180, 180)));


        // 🔹 Login logic
        loginBtn.addActionListener(e -> {
            String pin = new String(pinField.getPassword()).trim();
            try {
                boolean realMode = PinManager.verifyPin(pin);
                Dashboard dash = new Dashboard(realMode);
                dash.setVisible(true);
                this.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid PIN or error verifying.");
            }
        });

        // 🔹 Change PIN
        changePinBtn.addActionListener(e -> {
            String current = JOptionPane.showInputDialog(this, "Enter current PIN:");
            if (current == null) return;
            try {
                if (!PinManager.verifyPin(current.trim())) {
                    JOptionPane.showMessageDialog(this, "Incorrect current PIN!");
                    return;
                }
                String newPin = JOptionPane.showInputDialog(this, "Enter new PIN:");
                String confirm = JOptionPane.showInputDialog(this, "Confirm new PIN:");
                if (newPin != null && newPin.equals(confirm)) {
                    PinManager.savePin(newPin);
                    JOptionPane.showMessageDialog(this, "PIN changed successfully!");
                } else {
                    JOptionPane.showMessageDialog(this, "PINs didn’t match!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving new PIN: " + ex.getMessage());
            }
        });

        // 🔹 Exit
        exitBtn.addActionListener(e -> System.exit(0));
    }
}
