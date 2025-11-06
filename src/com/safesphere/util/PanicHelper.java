package com.safesphere.util;

import javax.swing.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.Comparator;

/**
 * Panic helper: aggressively tries to delete the data folder (both relative and based on user.dir),
 * prints status to console, and exits the JVM.
 *
 * WARNING: destructive — will delete files under the 'data' folder(s).
 */
public final class PanicHelper {
    private PanicHelper() {}

    public static void wipeAndExit(boolean showDialog) {
        // Optional user confirmation dialog
        if (showDialog) {
            try {
                JOptionPane.showMessageDialog(null,
                        "Panic Mode Activated!\nApp will delete local data and exit.",
                        "SafeSphere - Panic",
                        JOptionPane.WARNING_MESSAGE);
            } catch (Throwable ignored) { }
        }

        // Attempt deletions and log to console so you can see status in the Run window
        try {
            // Candidate data locations:
            // 1) relative "data" (Path.of("data"))
            // 2) project-based folder: user.dir + "/data"
            Path relative = Path.of("data");
            Path userDirBased = Path.of(System.getProperty("user.dir")).resolve("data");

            System.out.println("[PanicHelper] Starting wipe process...");
            System.out.println("[PanicHelper] user.dir = " + System.getProperty("user.dir"));
            System.out.println("[PanicHelper] relative data path = " + relative.toAbsolutePath());
            System.out.println("[PanicHelper] userDir data path = " + userDirBased.toAbsolutePath());

            // Try both paths (if they are the same this will effectively run once)
            deleteTreeIfExists(relative);
            deleteTreeIfExists(userDirBased);

            // Also attempt a handful of specific filenames just in case
            tryDeleteFile(userDirBased.resolve("realData.sfs"));
            tryDeleteFile(userDirBased.resolve("fakeData.sfs"));
            tryDeleteFile(userDirBased.resolve("pin.cfg"));
            tryDeleteFile(userDirBased.resolve("attempts.cfg"));

            tryDeleteFile(relative.resolve("realData.sfs"));
            tryDeleteFile(relative.resolve("fakeData.sfs"));
            tryDeleteFile(relative.resolve("pin.cfg"));
            tryDeleteFile(relative.resolve("attempts.cfg"));

            // After attempts, print final existence
            System.out.println("[PanicHelper] Final exists(relative) = " + Files.exists(relative));
            System.out.println("[PanicHelper] Final exists(userDirBased) = " + Files.exists(userDirBased));
            System.out.println("[PanicHelper] Wipe complete. Exiting now.");
        } catch (Throwable t) {
            System.err.println("[PanicHelper] Exception during wipe: " + t.getMessage());
            t.printStackTrace();
        } finally {
            // force exit
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            System.exit(0);
        }
    }

    private static void deleteTreeIfExists(Path dir) {
        try {
            if (Files.exists(dir)) {
                System.out.println("[PanicHelper] Deleting tree: " + dir.toAbsolutePath());
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                                System.out.println("[PanicHelper] deleted: " + path.toAbsolutePath());
                            } catch (IOException e) {
                                System.err.println("[PanicHelper] failed delete: " + path.toAbsolutePath() + " -> " + e.getMessage());
                            }
                        });
            } else {
                System.out.println("[PanicHelper] path does not exist: " + dir.toAbsolutePath());
            }
        } catch (Throwable t) {
            System.err.println("[PanicHelper] error walking/deleting " + dir.toAbsolutePath() + ": " + t.getMessage());
        }
    }

    private static void tryDeleteFile(Path p) {
        try {
            if (Files.exists(p)) {
                Files.deleteIfExists(p);
                System.out.println("[PanicHelper] deleted file: " + p.toAbsolutePath());
            } else {
                System.out.println("[PanicHelper] specific file not found: " + p.toAbsolutePath());
            }
        } catch (Throwable t) {
            System.err.println("[PanicHelper] unable to delete specific file " + p.toAbsolutePath() + ": " + t.getMessage());
        }
    }
}
