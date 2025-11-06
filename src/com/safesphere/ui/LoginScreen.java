package com.safesphere.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * LoginScreen:
 *  - correct PIN -> real dashboard, attempts reset
 *  - wrong PIN -> fake dashboard silently
 *  - on 5th wrong PIN -> silently trigger panic and exit immediately (no dialogs)
 *  - no "Incorrect PIN" popups shown
 *  - visible Panic button removed for login screen (panic triggered only automatically)
 */
public class LoginScreen extends JFrame {
    private static final int MAX_ATTEMPTS = 5;
    private final JPasswordField pinField;
    private final String realPin;
    private final String fakePin;
    private final Path attemptsFile = Path.of("data", "attempts.cfg");

    public LoginScreen() {
        // Load pins (try multiple strategies)
        String rp = "1234";
        String fp = "0000";

        // 1) try reflection on PinManager
        try {
            Class<?> pmClass = null;
            try {
                pmClass = Class.forName("com.safesphere.security.PinManager");
            } catch (ClassNotFoundException ignored) { }

            if (pmClass != null) {
                String[] staticNames = {"getRealPin", "getFakePin", "getReal", "getFake", "getPin", "getDefaultPin"};
                for (String name : staticNames) {
                    try {
                        Method m = pmClass.getMethod(name);
                        if ((m.getModifiers() & Modifier.STATIC) != 0 && m.getReturnType() == String.class) {
                            String val = (String) m.invoke(null);
                            if (val != null && !val.isEmpty()) {
                                if (name.toLowerCase().contains("real")) rp = val;
                                else if (name.toLowerCase().contains("fake")) fp = val;
                                else if ("1234".equals(rp)) rp = val;
                            }
                        }
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }
                }
                try {
                    Constructor<?> ctor = pmClass.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object inst = ctor.newInstance();
                    String[] instNames = {"getRealPin", "getFakePin", "getReal", "getFake", "getPin"};
                    for (String name : instNames) {
                        try {
                            Method m = pmClass.getMethod(name);
                            if ((m.getModifiers() & Modifier.STATIC) == 0 && m.getReturnType() == String.class) {
                                String val = (String) m.invoke(inst);
                                if (val != null && !val.isEmpty()) {
                                    if (name.toLowerCase().contains("real")) rp = val;
                                    else if (name.toLowerCase().contains("fake")) fp = val;
                                    else if ("1234".equals(rp)) rp = val;
                                }
                            }
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }
                    }
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ignored) { }
            }
        } catch (Throwable ignored) { }

        // 2) fallback to reading data/pin.cfg (format: real=1234  fake=0000)
        try {
            Path cfg = Path.of("data", "pin.cfg");
            if (Files.exists(cfg)) {
                List<String> lines = Files.readAllLines(cfg);
                for (String line : lines) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    int eq = s.indexOf('=');
                    if (eq > 0) {
                        String k = s.substring(0, eq).trim().toLowerCase();
                        String v = s.substring(eq + 1).trim();
                        if ((k.equals("real") || k.equals("realpin") || k.equals("real_pin")) && !v.isEmpty()) rp = v;
                        else if ((k.equals("fake") || k.equals("fakepin") || k.equals("fake_pin")) && !v.isEmpty()) fp = v;
                    }
                }
            }
        } catch (Throwable ignored) { }

        this.realPin = rp;
        this.fakePin = fp;

        // If attempts already at or above MAX, trigger panic immediately (silent)
        try {
            int current = readAttempts();
            if (current >= MAX_ATTEMPTS) {
                panicSilent();
            }
        } catch (Throwable ignored) { }

        // Build UI (no visible panic button)
        setTitle("SafeSphere - Login");
        setSize(380, 180);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Welcome to SafeSphere", SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        top.add(title, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        center.add(new JLabel("Enter PIN:"), gbc);

        pinField = new JPasswordField(12);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        center.add(pinField, gbc);

        add(center, BorderLayout.CENTER);

        // Buttons: only Login and Exit on left (no Panic)
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        JButton loginBtn = new JButton("Login");
        JButton exitBtn = new JButton("Exit");
        bottom.add(loginBtn);
        bottom.add(exitBtn);
        add(bottom, BorderLayout.SOUTH);

        // Enter -> login
        pinField.addActionListener(e -> attemptLogin());
        loginBtn.addActionListener(e -> attemptLogin());
        exitBtn.addActionListener(e -> System.exit(0));

        // focus quickly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                pinField.requestFocusInWindow();
            }
        });
    }

    private void attemptLogin() {
        char[] passChars = pinField.getPassword();
        String entered = (passChars == null) ? "" : new String(passChars).trim();
        if (passChars != null) java.util.Arrays.fill(passChars, '\0');

        if (entered.isEmpty()) {
            // no "incorrect" dialogs — just ask for entry
            pinField.requestFocusInWindow();
            return;
        }

        // If correct -> reset attempts and open real dashboard
        if (entered.equals(realPin)) {
            try { writeAttempts(0); } catch (Throwable ignored) {}
            openDashboard(true);
            return;
        }

        // Wrong attempt: increment attempts counter persistently
        int attempts = 0;
        try { attempts = readAttempts(); } catch (Throwable ignored) { attempts = 0; }
        attempts++;
        try { writeAttempts(attempts); } catch (Throwable ignored) {}

        // If reached MAX -> panic silently and exit fast
        if (attempts >= MAX_ATTEMPTS) {
            panicSilent();
            return;
        }

        // Otherwise open fake dashboard silently (no dialogs)
        openDashboard(false);
    }

    private void openDashboard(boolean realMode) {
        SwingUtilities.invokeLater(() -> {
            try {
                dispose();
                Dashboard d = new Dashboard(realMode);
                d.setVisible(true);
            } catch (Exception ex) {
                // In case of failure opening dashboard, exit quickly (we avoid showing details)
                try { System.exit(0); } catch (Throwable ignored) {}
            }
        });
    }

    // persist attempts (simple file with integer)
    private int readAttempts() {
        try {
            Path p = attemptsFile;
            if (!Files.exists(p)) return 0;
            String s = Files.readString(p).trim();
            if (s.isEmpty()) return 0;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void writeAttempts(int value) {
        try {
            Path dir = attemptsFile.getParent();
            if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
            Files.writeString(attemptsFile, String.valueOf(value));
        } catch (Throwable ignored) { }
    }

    // Immediate silent panic: no dialogs, quick cleanup (resets attempts) and exit
    private void panicSilent() {
        try {
            // optional: reset attempts file so locker doesn't persist if you want it cleared
            writeAttempts(0);
        } catch (Throwable ignored) {}
        try { dispose(); } catch (Throwable ignored) {}
        try { System.exit(0); } catch (Throwable ignored) {}
    }

    // quick test runner
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginScreen ls = new LoginScreen();
            ls.setVisible(true);
        });
    }
}

