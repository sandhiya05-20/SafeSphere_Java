package com.safesphere.data;

import java.util.List;

/**
 * DBRunner - simple runner to test DataStorageDB and to print out entries.
 * Replace the existing DBRunner.java contents with this file.
 */
public class DBRunner {
    public static void main(String[] args) throws Exception {
        // create DB helper
        DataStorageDB db = new DataStorageDB();

        // 1) Insert a fresh entry (so there is something to work with)
        System.out.println("Inserting a fresh entry...");
        db.saveEntry(1, "Note", "Original Title", "Original content");

        // 2) Load entries for owner 1 and print them
        System.out.println("\n--- Current entries for ownerId=1 ---");
        printEntriesForOwner(db, 1);

        // 3) Pick the first entry id and update it
        List<DataStorageDB.EntryItem> list = db.loadEntries(1);
        if (list.isEmpty()) {
            System.out.println("No entries found after insert — aborting test.");
            return;
        }

        int id = list.get(0).id;
        System.out.println("\nFound entry id=" + id + " title='" + list.get(0).title + "' content='" + list.get(0).content + "'");

        System.out.println("Updating entry id=" + id + " ...");
        db.updateEntry(id, "Updated Title", "Updated content here");

        // 4) Show entries again so we can see the updated content
        System.out.println("\n--- Entries after update ---");
        printEntriesForOwner(db, 1);

        // 5) Cleanup: delete the test entry
        System.out.println("\nDeleting test entry id=" + id);
        db.deleteEntry(id);

        System.out.println("Test finished.");
    }

    /**
     * Helper: load entries for an owner and print them to console.
     */
    private static void printEntriesForOwner(DataStorageDB db, int ownerId) {
        try {
            List<DataStorageDB.EntryItem> entries = db.loadEntries(ownerId);
            if (entries.isEmpty()) {
                System.out.println("No entries found for ownerId=" + ownerId);
                return;
            }
            for (DataStorageDB.EntryItem e : entries) {
                System.out.println("ID: " + e.id + " | Type: " + e.type + " | Title: " + e.title + " | Content: " + e.content + " | ModifiedAt: " + e.modifiedAt);
            }
        } catch (Exception ex) {
            System.err.println("Error while loading entries: ");
            ex.printStackTrace();
        }
    }
}
