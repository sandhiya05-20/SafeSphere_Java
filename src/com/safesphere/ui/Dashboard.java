package com.safesphere.ui;

import com.safesphere.data.DataStorage;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Vector;

public class Dashboard extends JFrame {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final DataStorage storage;
    private final boolean realMode;

    public Dashboard(boolean realMode) {
        this.realMode = realMode;
        String path = realMode ? "data/realData.sfs" : "data/fakeData.sfs";
        storage = new DataStorage(path);

        setTitle((realMode ? "SafeSphere Dashboard - Real Mode" : "SafeSphere Dashboard - Fake Mode") + " - updated by Archana");
        setSize(600, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10,10));

        // 🔹 Header
        JLabel header = new JLabel(
                realMode ? "🟢 REAL MODE - Your Encrypted Data" : "🟠 FAKE MODE - Decoy Data",
                SwingConstants.CENTER
        );
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        header.setOpaque(true);
        header.setForeground(Color.WHITE);
        header.setBackground(realMode ? new Color(0, 153, 51) : new Color(204, 102, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        add(header, BorderLayout.NORTH);

        // 🔹 Data area
        try {
            List<String> saved = storage.loadData();
            if (saved.isEmpty()) populateDemo();
            else saved.forEach(model::addElement);
        } catch (Exception e) {
            populateDemo();
        }

        JList<String> list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 13));
        add(new JScrollPane(list), BorderLayout.CENTER);

        // 🔹 Buttons
        JPanel bottom = new JPanel(new FlowLayout());
        JButton add = new JButton("Add Entry");
        JButton save = new JButton("Save Data");
        JButton panic = new JButton("Activate Panic Mode");
        bottom.add(add);
        bottom.add(save);
        bottom.add(panic);
        add(bottom, BorderLayout.SOUTH);

        // 🔹 Button actions
        add.addActionListener(e -> {
            String val = JOptionPane.showInputDialog(this, "Enter new data item:");
            if (val != null && !val.trim().isEmpty()) model.addElement(val.trim());
        });

        save.addActionListener(e -> {
            try {
                java.util.List<String> out = new Vector<>();
                for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
                storage.saveData(out);
                JOptionPane.showMessageDialog(this, "Data saved securely.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving data: " + ex.getMessage());
            }
        });

        panic.addActionListener(e -> activatePanicMode());
    }

    private void populateDemo() {
        if (realMode) {
            model.addElement("Bank: SafeBank - ****4567 - Balance: $3,800.00");
            model.addElement("Email: me@safemail.com - passwd: ********");
            model.addElement("Note: PAN: ABXPM1234R");
        } else {
            model.addElement("Bank: FirstTrust - ****9988 - Balance: $521.00");
            model.addElement("Contact: Random User - 9876543210");
            model.addElement("Note: Meeting at HQ - 10 AM");
        }
    }

    private void activatePanicMode() {
        JOptionPane.showMessageDialog(this,
                "🛑 Panic Mode Activated!\nApp will close and clear display.",
                "SafeSphere", JOptionPane.WARNING_MESSAGE);
        dispose();
        System.exit(0);
    }
}
