package com.sfw.wholesale.license;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Communicates with the Firebase Realtime Database REST API to verify licenses.
 */
public class LicenseClient {

    private static final String FIREBASE_URL = "https://inventory-app-ad18a-default-rtdb.firebaseio.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public enum LicenseStatus {
        VALID,
        INVALID_KEY,
        REVOKED,
        BOUND_TO_OTHER_PC,
        NETWORK_ERROR
    }

    public static class VerificationResult {
        public final LicenseStatus status;
        public final String message;

        public VerificationResult(LicenseStatus status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    /**
     * Verifies a product key against Firebase.
     * If the key exists and has no hardwareId, it binds the key to this PC.
     * If the key exists and has a hardwareId, it checks if it matches this PC.
     */
    public static VerificationResult verifyLicense(String productKey) {
        String hardwareId = HardwareUtils.getHardwareId();
        if (hardwareId == null || hardwareId.isBlank()) {
            return new VerificationResult(LicenseStatus.INVALID_KEY, "Could not generate hardware ID for this PC.");
        }

        try {
            // Firebase REST API requires .json at the end of the path
            String url = FIREBASE_URL + "/licenses/" + productKey + ".json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() != 200) {
                return new VerificationResult(LicenseStatus.NETWORK_ERROR, "Server returned " + response.statusCode());
            }

            if (body == null || body.equals("null")) {
                return new VerificationResult(LicenseStatus.INVALID_KEY, "This Product Key does not exist.");
            }

            // Simple string extraction since we don't have a JSON parser dependency
            String status = extractJsonValue(body, "status");
            String registeredHardwareId = extractJsonValue(body, "hardwareId");

            if ("REVOKED".equalsIgnoreCase(status)) {
                return new VerificationResult(LicenseStatus.REVOKED, "This license key has been revoked.");
            }

            if (registeredHardwareId == null || registeredHardwareId.isBlank()) {
                // First time activation! Bind to this PC.
                boolean success = bindHardwareIdToLicense(productKey, hardwareId);
                if (success) {
                    return new VerificationResult(LicenseStatus.VALID, "Activation successful.");
                } else {
                    return new VerificationResult(LicenseStatus.NETWORK_ERROR, "Failed to bind license to this PC.");
                }
            } else if (registeredHardwareId.equals(hardwareId)) {
                // Normal successful verification
                return new VerificationResult(LicenseStatus.VALID, "License verified.");
            } else {
                return new VerificationResult(LicenseStatus.BOUND_TO_OTHER_PC, "This Product Key is already activated on another computer.");
            }

        } catch (Exception e) {
            return new VerificationResult(LicenseStatus.NETWORK_ERROR, "Network error: " + e.getMessage());
        }
    }

    /**
     * Updates the Firebase record to bind the hardwareId.
     */
    private static boolean bindHardwareIdToLicense(String productKey, String hardwareId) {
        try {
            String url = FIREBASE_URL + "/licenses/" + productKey + ".json";
            String jsonPayload = "{\"hardwareId\":\"" + hardwareId + "\"}";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A very basic JSON string extractor for flat key-value pairs.
     * e.g. extracts "ACTIVE" from {"status":"ACTIVE","hardwareId":""}
     */
    private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return null;
        
        int colonIndex = json.indexOf(':', keyIndex);
        if (colonIndex == -1) return null;

        int valueStart = -1;
        int valueEnd = -1;
        
        // Find the start of the value (the first quote)
        for (int i = colonIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                valueStart = i + 1;
                break;
            }
        }
        
        if (valueStart != -1) {
            // Find the end of the value (the next quote)
            for (int i = valueStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    valueEnd = i;
                    break;
                }
            }
        }
        
        if (valueStart != -1 && valueEnd != -1) {
            return json.substring(valueStart, valueEnd);
        }
        return null;
    }
}
