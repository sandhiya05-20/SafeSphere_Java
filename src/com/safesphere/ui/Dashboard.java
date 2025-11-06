package com.safesphere.ui;

import com.safesphere.data.DataStorage;
import com.safesphere.util.PanicHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Vector;

public class Dashboard extends JFrame {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final DataStorage storage;
    private final boolean realMode;
    private JList<String> list;

    public Dashboard(boolean realMode) {
        this.realMode = realMode;
        String path = realMode ? "data/realData.sfs" : "data/fakeData.sfs";
        storage = new DataStorage(path);

        setTitle("SafeSphere Dashboard");
        setSize(600, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Neutral header
        JLabel header = new JLabel("SafeSphere Dashboard", SwingConstants.CENTER);
        header.setFont(new Font("SansSerif", Font.BOLD, 18));
        header.setOpaque(true);
        header.setForeground(Color.WHITE);
        header.setBackground(new Color(45, 70, 140));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(header, BorderLayout.NORTH);

        // Load saved data
        try {
            List<String> saved = storage.loadData();
            if (saved.isEmpty()) populateDemo();
            else saved.forEach(model::addElement);
        } catch (Exception e) {
            populateDemo();
        }

        list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 13));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(list), BorderLayout.CENTER);

        // Buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        JButton addBtn = new JButton("Add Entry");
        JButton deleteBtn = new JButton("Delete Entry");
        JButton saveBtn = new JButton("Save Data");
        JButton panicBtn = new JButton("Activate Panic Mode");

        deleteBtn.setEnabled(false);
        bottom.add(addBtn);
        bottom.add(deleteBtn);
        bottom.add(saveBtn);
        bottom.add(panicBtn);
        add(bottom, BorderLayout.SOUTH);

        // selection -> enable delete
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                deleteBtn.setEnabled(list.getSelectedIndex() != -1);
            }
        });

        // double-click to edit
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int idx = list.locationToIndex(evt.getPoint());
                    if (idx >= 0) {
                        String old = model.getElementAt(idx);
                        String edited = JOptionPane.showInputDialog(Dashboard.this, "Edit entry:", old);
                        if (edited != null && !edited.trim().isEmpty()) {
                            model.setElementAt(edited.trim(), idx);
                        }
                    }
                }
            }
        });

        // keyboard Delete key deletes selected item with confirmation
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteItem");
        list.getActionMap().put("deleteItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performDelete();
            }
        });

        // Add action
        addBtn.addActionListener(e -> {
            String val = JOptionPane.showInputDialog(this, "Enter new data item:");
            if (val != null && !val.trim().isEmpty()) model.addElement(val.trim());
        });

        // Delete action
        deleteBtn.addActionListener(e -> performDelete());

        // Save action
        saveBtn.addActionListener(e -> {
            try {
                java.util.List<String> out = new Vector<>();
                for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
                storage.saveData(out);
                JOptionPane.showMessageDialog(this, "Data saved securely.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving data: " + ex.getMessage());
            }
        });

        // Panic button now wipes and exits using PanicHelper
        panicBtn.addActionListener(e -> {
            // Show dialog then wipe & exit (helper prints console logs)
            PanicHelper.wipeAndExit(true);
        });
    }

    private void performDelete() {
        int idx = list.getSelectedIndex();
        if (idx == -1) return;

        String item = model.getElementAt(idx);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this entry?\n\n" + item,
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            model.remove(idx);
            try {
                java.util.List<String> out = new Vector<>();
                for (int i = 0; i < model.size(); i++) out.add(model.getElementAt(i));
                storage.saveData(out);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Warning: failed to save after delete: " + ex.getMessage());
            }
        }
    }

    private void populateDemo() {
        model.addElement("Bank: SafeBank - ****4567 - Balance: $3,800.00");
        model.addElement("Email: me@safemail.com - passwd: ********");
        model.addElement("Note: PAN: ABXPM1234R");
    }
}

