package com.sfw.wholesale.license;

import com.sfw.wholesale.database.DatabaseManager;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Manages the local license file, including tamper protection and the grace period.
 */
public class LicenseManager {

    private static final Logger LOG = Logger.getLogger(LicenseManager.class.getName());
    
    // 96 hours (4 days) offline grace period
    public static final long GRACE_PERIOD_SECONDS = 96 * 60 * 60; 
    
    // Secret salt to prevent users from manually modifying the timestamp in the file
    private static final String SECRET_SALT = "SfwWholesaleSuperSecretKey99!";
    
    private static final String LICENSE_FILE = "license.dat";

    public static class LocalLicense {
        public final String productKey;
        public final long lastVerifiedEpochSeconds;

        public LocalLicense(String productKey, long lastVerifiedEpochSeconds) {
            this.productKey = productKey;
            this.lastVerifiedEpochSeconds = lastVerifiedEpochSeconds;
        }
    }

    private static Path getLicenseFilePath() {
        // Store next to the database file (usually user home or current dir)
        String dbPathStr = DatabaseManager.resolveDbPath();
        Path dbPath = Paths.get(dbPathStr);
        if (dbPath.getParent() != null) {
            return dbPath.getParent().resolve(LICENSE_FILE);
        }
        return Paths.get(LICENSE_FILE);
    }

    /**
     * Reads and verifies the local license file.
     * Returns null if missing, corrupted, or tampered with.
     */
    public static LocalLicense getLocalLicense() {
        Path path = getLicenseFilePath();
        if (!Files.exists(path)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(is);

            String key = props.getProperty("productKey");
            String timestampStr = props.getProperty("lastVerified");
            String signature = props.getProperty("signature");

            if (key == null || timestampStr == null || signature == null) {
                return null;
            }

            // Verify signature to ensure timestamp wasn't tampered with
            String expectedSignature = generateSignature(key, timestampStr);
            if (!expectedSignature.equals(signature)) {
                LOG.warning("License file signature mismatch. Tampering detected.");
                return null;
            }

            long timestamp = Long.parseLong(timestampStr);
            return new LocalLicense(key, timestamp);

        } catch (Exception e) {
            LOG.warning("Failed to read local license file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves the product key and current timestamp to the local license file.
     */
    public static void saveLocalLicense(String productKey) {
        Path path = getLicenseFilePath();
        String timestampStr = String.valueOf(Instant.now().getEpochSecond());
        String signature = generateSignature(productKey, timestampStr);

        Properties props = new Properties();
        props.setProperty("productKey", productKey);
        props.setProperty("lastVerified", timestampStr);
        props.setProperty("signature", signature);

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "SFW Wholesale License - Do not modify");
        } catch (IOException e) {
            LOG.severe("Could not save license file: " + e.getMessage());
        }
    }
    
    /**
     * Deletes the local license file (e.g., if revoked).
     */
    public static void clearLocalLicense() {
        try {
            Files.deleteIfExists(getLicenseFilePath());
        } catch (IOException e) {
            LOG.warning("Could not delete license file: " + e.getMessage());
        }
    }

    /**
     * Returns true if the license was verified within the grace period.
     */
    public static boolean isWithinGracePeriod(LocalLicense license) {
        if (license == null) return false;
        long now = Instant.now().getEpochSecond();
        long diff = now - license.lastVerifiedEpochSeconds;
        return diff >= 0 && diff <= GRACE_PERIOD_SECONDS;
    }

    /**
     * Calculates how many hours are left in the grace period.
     */
    public static int getRemainingGracePeriodHours(LocalLicense license) {
        if (license == null) return 0;
        long now = Instant.now().getEpochSecond();
        long diff = now - license.lastVerifiedEpochSeconds;
        long remainingSecs = GRACE_PERIOD_SECONDS - diff;
        if (remainingSecs < 0) return 0;
        return (int) (remainingSecs / 3600);
    }

    private static String generateSignature(String productKey, String timestampStr) {
        String input = productKey + "|" + timestampStr + "|" + HardwareUtils.getHardwareId() + "|" + SECRET_SALT;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
