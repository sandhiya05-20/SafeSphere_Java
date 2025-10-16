package com.safesphere.data;

import com.safesphere.security.EncryptionManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DataStorage {
    private final String filePath;

    public DataStorage(String filePath) {
        this.filePath = filePath;
        // ensure folder exists
        File f = new File(filePath).getParentFile();
        if (f != null && !f.exists()) f.mkdirs();
    }

    public void saveData(List<String> entries) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String s : entries) {
            sb.append(s.replace("\n", " ")).append("\n");
        }
        String encrypted = EncryptionManager.encrypt(sb.toString());
        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(encrypted);
        }
    }

    public List<String> loadData() throws Exception {
        File file = new File(filePath);
        if (!file.exists()) return new ArrayList<>();
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        String encrypted = new String(bytes);
        if (encrypted.trim().isEmpty()) return new ArrayList<>();
        String decrypted = EncryptionManager.decrypt(encrypted);
        String[] lines = decrypted.split("\\r?\\n");
        List<String> list = new ArrayList<>();
        for (String l : lines) if (!l.trim().isEmpty()) list.add(l);
        return list;
    }
}
