package com.safesphere.ui;

import com.safesphere.data.DataStorage;
import com.safesphere.data.DataStorageDB;
import com.safesphere.data.DataStorageDB.EntryItem;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Vector;

/**
 * Dashboard Swing window with user-aware DB.
 * - Takes ownerId and realMode in constructor
 * - Uses ownerId for all DB operations
 */
public class Dashboard extends JFrame {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final DataStorage storage;
    private final boolean realMode;
    private final int ownerId; // <-- NEW FIELD

    // UI controls for DB view
    private JTable dbTable;
    private DefaultTableModel dbTableModel;
    private JButton refreshDbButton;

    // Default constructor keeps backward compatibility
    public Dashboard(boolean realMode) {
        this(1, realMode); // default ownerId = 1
    }

    // New constructor for per-user dashboards
    public Dashboard(int ownerId, boolean realMode) {
        this.ownerId = ownerId;
        this.realMode = realMode;
        String path = realMode ? "data/realData.sfs" : "data/fakeData.sfs";
        storage = new DataStorage(path);

        setTitle((realMode ? "SafeSphere Dashboard - Real Mode" : "SafeSphere Dashboard - Fake Mode")
                + " | User ID: " + ownerId);
        setSize(950, 540);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10,10));

        // Header
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

        // Load file-backed list
        try {
            java.util.List<String> saved = storage.loadData();
            if (saved.isEmpty()) populateDemo();
            else saved.forEach(model::addElement);
        } catch (Exception e) {
            populateDemo();
        }

        JList<String> list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane leftScroll = new JScrollPane(list);

        // If realMode, create DB table on the right; otherwise show placeholder info
        Component rightComponent;
        if (realMode) {
            rightComponent = createDbPanel();
        } else {
            JPanel placeholder = new JPanel(new BorderLayout());
            JLabel info = new JLabel("<html><center>Fake mode — DB view disabled.<br>Switch to realMode to see DB entries.</center></html>", SwingConstants.CENTER);
            info.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
            placeholder.add(info, BorderLayout.CENTER);
            rightComponent = placeholder;
        }

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightComponent);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);

        // Bottom Buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton add = new JButton("Add Entry");
        JButton save = new JButton("Save Data");
        JButton panic = new JButton("Activate Panic Mode");
        bottom.add(add);
        bottom.add(save);

        // Add DB entry button (real mode only)
        JButton addDb = new JButton("Add DB Entry");
        if (realMode) bottom.add(addDb);

        // Refresh button (real mode only)
        if (realMode) {
            refreshDbButton = new JButton("Refresh DB");
            bottom.add(refreshDbButton);
            refreshDbButton.addActionListener(e -> refreshDbTable());
        }

        // Edit/Delete buttons (real mode only)
        if (realMode) {
            JButton editSelected = new JButton("Edit Selected");
            JButton deleteSelected = new JButton("Delete Selected");
            bottom.add(editSelected);
            bottom.add(deleteSelected);

            editSelected.addActionListener(e -> editSelectedRow());
            deleteSelected.addActionListener(e -> deleteSelectedRow());
        }

        bottom.add(panic);
        add(bottom, BorderLayout.SOUTH);

        // Button actions
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

        // Add DB entry
        addDb.addActionListener(e -> showAddDbEntryDialog());

        panic.addActionListener(e -> activatePanicMode());

        // Load DB entries at startup
        if (realMode) refreshDbTable();
    }

    private JPanel createDbPanel() {
        JPanel panel = new JPanel(new BorderLayout(8,8));
        dbTableModel = new DefaultTableModel(new String[]{"ID","Type","Title","Content","ModifiedAt"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dbTable = new JTable(dbTableModel);
        dbTable.setAutoCreateRowSorter(true);
        dbTable.setFillsViewportHeight(true);
        dbTable.setRowHeight(22);
        panel.add(new JLabel("Database entries (ownerId = " + ownerId + ")"), BorderLayout.NORTH);
        panel.add(new JScrollPane(dbTable), BorderLayout.CENTER);
        return panel;
    }

    private void refreshDbTable() {
        if (!realMode || dbTableModel == null) return;
        dbTableModel.setRowCount(0);
        try {
            DataStorageDB db = new DataStorageDB();
            List<EntryItem> entries = db.loadEntries(ownerId);
            for (EntryItem e : entries) {
                dbTableModel.addRow(new Object[]{
                        e.id, e.type, e.title, e.content, e.modifiedAt
                });
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load DB entries: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void showAddDbEntryDialog() {
        if (!realMode) {
            JOptionPane.showMessageDialog(this, "DB operations are disabled in fake mode.");
            return;
        }
        JPanel form = new JPanel(new BorderLayout(6,6));
        JPanel top = new JPanel(new GridLayout(2,2,6,6));
        top.add(new JLabel("Type:"));
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"Note", "Password", "SecureNote"});
        top.add(typeBox);
        top.add(new JLabel("Title:"));
        JTextField titleField = new JTextField();
        top.add(titleField);
        form.add(top, BorderLayout.NORTH);
        form.add(new JLabel("Content:"), BorderLayout.CENTER);
        JTextArea contentArea = new JTextArea(6, 40);
        form.add(new JScrollPane(contentArea), BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, form, "Add DB Entry", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String type = (String) typeBox.getSelectedItem();
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();
        if (title.isEmpty() && content.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title or Content required.");
            return;
        }
        try {
            DataStorageDB db = new DataStorageDB();
            db.saveEntry(ownerId, type, title, content);
            JOptionPane.showMessageDialog(this, "DB entry saved.");
            refreshDbTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save DB entry: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void editSelectedRow() {
        int row = dbTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a row to edit.");
            return;
        }
        int modelRow = dbTable.convertRowIndexToModel(row);
        int id = ((Number) dbTableModel.getValueAt(modelRow, 0)).intValue();
        String currentType = (String) dbTableModel.getValueAt(modelRow, 1);
        String currentTitle = (String) dbTableModel.getValueAt(modelRow, 2);
        String currentContent = (String) dbTableModel.getValueAt(modelRow, 3);

        JPanel form = new JPanel(new BorderLayout(6,6));
        JPanel top = new JPanel(new GridLayout(2,2,6,6));
        top.add(new JLabel("Type:"));
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"Note","Password","SecureNote"});
        typeBox.setSelectedItem(currentType);
        top.add(typeBox);
        top.add(new JLabel("Title:"));
        JTextField titleField = new JTextField(currentTitle);
        top.add(titleField);
        form.add(top, BorderLayout.NORTH);
        form.add(new JLabel("Content:"), BorderLayout.CENTER);
        JTextArea contentArea = new JTextArea(6, 40);
        contentArea.setText(currentContent);
        form.add(new JScrollPane(contentArea), BorderLayout.SOUTH);

        int res = JOptionPane.showConfirmDialog(this, form, "Edit Entry (id=" + id + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        try {
            DataStorageDB db = new DataStorageDB();
            db.updateEntry(id, titleField.getText().trim(), contentArea.getText().trim());
            JOptionPane.showMessageDialog(this, "Entry updated.");
            refreshDbTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update entry: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void deleteSelectedRow() {
        int row = dbTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Select a row to delete.");
            return;
        }
        int modelRow = dbTable.convertRowIndexToModel(row);
        int id = ((Number) dbTableModel.getValueAt(modelRow, 0)).intValue();
        int confirm = JOptionPane.showConfirmDialog(this, "Delete entry id=" + id + "?", "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            DataStorageDB db = new DataStorageDB();
            db.deleteEntry(id);
            JOptionPane.showMessageDialog(this, "Entry deleted.");
            refreshDbTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete entry: " + ex.getMessage());
            ex.printStackTrace();
        }
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