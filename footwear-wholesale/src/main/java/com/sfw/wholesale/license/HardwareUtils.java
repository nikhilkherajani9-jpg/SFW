package com.sfw.wholesale.license;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;

/**
 * Utility to generate a unique, stable hardware fingerprint for the machine.
 */
public class HardwareUtils {

    /**
     * Gets a unique hardware ID based on the motherboard/system UUID.
     * Falls back to a username-based hash if wmic fails.
     */
    public static String getHardwareId() {
        String uuid = getWindowsUuid();
        if (uuid == null || uuid.isBlank()) {
            // Fallback for non-Windows or if wmic is missing
            uuid = System.getProperty("user.name") + "-" + System.getProperty("os.name");
        }
        return hash(uuid);
    }

    private static String getWindowsUuid() {
        try {
            Process process = Runtime.getRuntime().exec("wmic csproduct get uuid");
            process.getOutputStream().close();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean isNextLineUuid = false;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (isNextLineUuid) {
                        return line; // This is the actual UUID
                    }
                    if (line.equalsIgnoreCase("UUID")) {
                        isNextLineUuid = true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and fallback
        }
        return null;
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // Return first 16 chars for a clean, short hardware ID
            return hexString.toString().substring(0, 16).toUpperCase();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode()).toUpperCase();
        }
    }
}
