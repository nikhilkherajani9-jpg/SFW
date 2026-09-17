package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.model.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javafx.scene.image.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * QR Sync service — pairs the desktop app with the Flutter mobile app over a
 * local WiFi hotspot. No internet, no router required.
 *
 * Flow:
 *  1. Desktop user turns on Windows Mobile Hotspot manually.
 *  2. App detects local IP address (on the hotspot subnet).
 *  3. App generates a one-time UUID token.
 *  4. App starts a minimal HTTP server bound to that IP on port 8742.
 *  5. App generates a QR containing: {ssid, ip, port, token}.
 *  6. Phone scans QR → connects to hotspot → calls GET /data?token=<token>.
 *  7. Server validates token, responds with gzipped JSON payload.
 *  8. Token is invalidated after the first successful /data call.
 *  9. Subsequent requests with the same token get 403.
 */
public class SyncService {

    private static final Logger LOG         = Logger.getLogger(SyncService.class.getName());
    private static final int    SERVER_PORT = 8742;
    // BUG-10: cryptographically strong RNG for the pairing PIN
    private static final java.security.SecureRandom SECURE_RNG = new java.security.SecureRandom();

    private static final byte[] AES_KEY = "SfwMobileAppSyncDataKey123456789".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_IV  = "SfwMobileAppIV12".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private volatile String  currentToken;   // BUG-13: volatile for cross-thread visibility
    private volatile boolean tokenUsed;      // BUG-13: volatile for cross-thread visibility
    private String     boundIp;

    private final DataCache cache = DataCache.getInstance();

    // ── Public API ────────────────────────────────────────────────────────────

    private boolean syncStock = true;
    private boolean syncLrs = true;

    /**
     * Starts the local HTTP server and returns a JavaFX Image of the QR code.
     * If a server is already running, stops it first.
     *
     * @param hotspotSsid  the SSID of the Windows hotspot (typed by user)
     * @param selectedIp   the local IP address selected by the user (from NetworkUtil)
     * @return  JavaFX WritableImage containing the QR code (400×400 px)
     * @throws Exception on server start or QR generation failure
     */
    public Image startAndGenerateQr(String hotspotSsid, String selectedIp, boolean syncStock, boolean syncLrs) throws Exception {
        stop(); // ensure clean state

        this.boundIp = selectedIp;
        // BUG-10: use SecureRandom for the pairing PIN (6 digits, 1M possibilities)
        this.currentToken = String.format("%06d", SECURE_RNG.nextInt(1_000_000));
        this.tokenUsed = false;
        this.syncStock = syncStock;
        this.syncLrs = syncLrs;

        startServer();

        String qrPayload = buildQrPayload(hotspotSsid, selectedIp, currentToken);
        LOG.info("QR payload: " + qrPayload);
        return generateQrImage(qrPayload, 400);
    }

    /** Starts the server for USB ADB tunnel without QR code generation */
    public void startServerForUsb(boolean syncStock, boolean syncLrs) throws Exception {
        stop();
        this.boundIp = "127.0.0.1";
        this.currentToken = "USB";
        this.tokenUsed = false;
        this.syncStock = syncStock;
        this.syncLrs = syncLrs;
        startServer();
    }

    /** Generates an AES-encrypted, gzipped JSON payload for file export */
    public byte[] generateEncryptedExport(boolean syncStock, boolean syncLrs) throws Exception {
        this.syncStock = syncStock;
        this.syncLrs = syncLrs;
        
        byte[] payload = buildJsonPayload();
        byte[] compressed = gzip(payload);
        
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(AES_KEY, "AES");
        javax.crypto.spec.IvParameterSpec ivSpec = new javax.crypto.spec.IvParameterSpec(AES_IV);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        
        return cipher.doFinal(compressed);
    }

    /** Stops the HTTP server if it is running. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            LOG.info("Sync server stopped.");
        }
    }

    public boolean isRunning() { return server != null; }

    public String getBoundIp()    { return boundIp; }
    public int    getServerPort() { return SERVER_PORT; }
    public String getCurrentToken() { return currentToken; }

    // ── HTTP server ────────────────────────────────────────────────────────────

    private void startServer() throws IOException {
        // Bind to 0.0.0.0 (all interfaces) to prevent routing/binding issues on virtual adapters.
        // The phone will still connect via the specific boundIp provided in the QR code.
        InetSocketAddress addr = new InetSocketAddress(SERVER_PORT);
        server = HttpServer.create(addr, 10);

        // GET /ping — returns 200 if token matches (lets phone verify pairing)
        server.createContext("/ping", exchange -> {
            String token = queryParam(exchange.getRequestURI(), "token");
            if (!isValidToken(exchange, token)) {
                respond(exchange, 403, "application/json", "{\"error\":\"invalid_token\"}");
                return;
            }
            respond(exchange, 200, "application/json", "{\"status\":\"ok\"}");
        });

        // GET /api/sync — returns gzipped JSON payload (one-time use)
        server.createContext("/api/sync", exchange -> {
            String token = queryParam(exchange.getRequestURI(), "pin");
            if (!isValidToken(exchange, token)) {
                respond(exchange, 403, "application/json", "{\"error\":\"invalid_token\"}");
                return;
            }

            // Invalidate token immediately before generating data
            tokenUsed = true;

            try {
                byte[] data = buildJsonPayload();
                byte[] compressed = gzip(data);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(200, compressed.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(compressed);
                }
                LOG.info("Data payload sent to mobile client. Token invalidated.");
            } catch (Exception e) {
                respond(exchange, 500, "application/json",
                        "{\"error\":\"server_error\",\"msg\":\"" + e.getMessage() + "\"}");
            }
        });

        // Serve PWA static files for everything else
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") || path.isEmpty()) {
                    path = "/index.html";
                }
                
                try (InputStream is = getClass().getResourceAsStream("/mobile-app" + path)) {
                    if (is == null) {
                        respond(exchange, 404, "text/plain", "Not found");
                        return;
                    }
                    
                    String contentType = "text/plain";
                    if (path.endsWith(".html")) contentType = "text/html";
                    else if (path.endsWith(".css")) contentType = "text/css";
                    else if (path.endsWith(".js")) contentType = "application/javascript";
                    else if (path.endsWith(".json")) contentType = "application/json";
                    else if (path.endsWith(".png")) contentType = "image/png";
                    
                    byte[] content = is.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.sendResponseHeaders(200, content.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(content);
                    }
                } catch (Exception e) {
                    respond(exchange, 500, "text/plain", "Server error");
                }
            } else {
                respond(exchange, 405, "text/plain", "Method not allowed");
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        LOG.info("Sync server started at " + boundIp + ":" + SERVER_PORT);
    }

    private boolean isValidToken(HttpExchange ex, String token) {
        String remoteAddress = ex.getRemoteAddress().getAddress().getHostAddress();
        if (("127.0.0.1".equals(remoteAddress) || "0:0:0:0:0:0:0:1".equals(remoteAddress)) && "USB".equals(token)) {
            return true;
        }
        return !tokenUsed
                && currentToken != null
                && currentToken.equals(token);
    }

    private void respond(HttpExchange ex, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private String queryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ── JSON payload ──────────────────────────────────────────────────────────

    /**
     * Builds the full JSON payload from the in-memory cache.
     * Hand-written — no Jackson/Gson dependency.
     * Includes: stock, lr_entries, lr_items, creditors.
     * Excludes: gd_transfers (not needed by mobile view).
     */
    private byte[] buildJsonPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"exported_at\":\"").append(java.time.LocalDateTime.now()).append("\",");

        // Stock
        sb.append("\"stock\":[");
        if (syncStock) {
            List<StockRow> stock = cache.getStockRows();
            for (int i = 0; i < stock.size(); i++) {
                StockRow r = stock.get(i);
                if (i > 0) sb.append(',');
                sb.append("{")
                  .append("\"product\":").append(jsonStr(r.getProductName())).append(',')
                  .append("\"location\":").append(jsonStr(r.getLocation())).append(',')
                  .append("\"cartons\":").append(r.getCartons()).append(',')
                  .append("\"ppc\":").append(r.getPairsPerCarton()).append(',')
                  .append("\"total_pairs\":").append(r.getTotalPairs()).append(',')
                  .append("\"lr_source\":").append(jsonStr(r.getLrSource()))
                  .append("}");
            }
        }
        sb.append("],");

        // LR entries
        sb.append("\"lr_entries\":[");
        if (syncLrs) {
            List<LrEntry> lrs = cache.getLrList();
            for (int i = 0; i < lrs.size(); i++) {
                LrEntry e = lrs.get(i);
                if (i > 0) sb.append(',');
                sb.append("{")
                  .append("\"lr_number\":").append(jsonStr(e.getLrNumber())).append(',')
                  .append("\"lr_date\":").append(jsonStr(e.getLrDate() != null ? e.getLrDate().toString() : "")).append(',')
                  .append("\"transport\":").append(jsonStr(e.getTransportCompany())).append(',')
                  .append("\"total_cartons\":").append(e.getTotalCartons()).append(',')
                  .append("\"total_shop_cartons\":").append(e.getTotalShopCartons())
                  .append("}");
            }
        }
        sb.append("],");

        // LR items
        sb.append("\"lr_items\":[");
        if (syncLrs) {
            String sql = "SELECT i.line_no, i.product_name, i.cartons, i.shop_cartons, i.pairs_per_carton, i.location, e.lr_number " +
                         "FROM lr_items i JOIN lr_entries e ON i.lr_id = e.id " +
                         "WHERE e.lr_number != 'MANUAL-ADJ'";
            // BUG-06: use a dedicated read connection — never share the FXAT's write connection
            try (java.sql.Connection conn = DatabaseManager.getInstance().openReadConnection();
                 java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append("{")
                      .append("\"lr_number\":").append(jsonStr(rs.getString("lr_number"))).append(',')
                      .append("\"line_no\":").append(rs.getInt("line_no")).append(',')
                      .append("\"product\":").append(jsonStr(rs.getString("product_name"))).append(',')
                      .append("\"cartons\":").append(rs.getInt("cartons")).append(',')
                      .append("\"shop_cartons\":").append(rs.getInt("shop_cartons")).append(',')
                      .append("\"ppc\":").append(rs.getInt("pairs_per_carton")).append(',')
                      .append("\"location\":").append(jsonStr(rs.getString("location")))
                      .append("}");
                }
            } catch (java.sql.SQLException ex) {
                LOG.warning("Failed to fetch lr_items for sync: " + ex.getMessage());
            }
        }
        sb.append("],");


        // Stock Ledger
        sb.append("\"stock_ledger\":[");
        if (syncStock) {
            // BUG-05+06: dedicated read connection + try-with-resources (both Statement and RS always closed)
            try (java.sql.Connection conn = DatabaseManager.getInstance().openReadConnection();
                 java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery(
                     "SELECT product_name, location, transaction_date, inward_cartons, outward_cartons," +
                     " balance_cartons, pairs_per_carton, lr_source, transaction_type" +
                     " FROM stock_ledger ORDER BY id ASC")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append("{")
                      .append("\"product\":").append(jsonStr(rs.getString("product_name"))).append(',')
                      .append("\"location\":").append(jsonStr(rs.getString("location"))).append(',')
                      .append("\"date\":").append(jsonStr(rs.getString("transaction_date"))).append(',')
                      .append("\"inward\":").append(rs.getInt("inward_cartons")).append(',')
                      .append("\"outward\":").append(rs.getInt("outward_cartons")).append(',')
                      .append("\"balance\":").append(rs.getInt("balance_cartons")).append(',')
                      .append("\"ppc\":").append(rs.getInt("pairs_per_carton")).append(',')
                      .append("\"lr_source\":").append(jsonStr(rs.getString("lr_source"))).append(',')
                      .append("\"type\":").append(jsonStr(rs.getString("transaction_type")))
                      .append("}");
                }
            } catch (Exception e) {
                LOG.warning("Failed to append stock_ledger to sync payload: " + e.getMessage());
            }
        }
        sb.append("]");

        sb.append("}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(data);
        }
        return baos.toByteArray();
    }

    // ── QR code generation ─────────────────────────────────────────────────────

    private String buildQrPayload(String ssid, String ip, String token) {
        return "http://" + ip + ":" + SERVER_PORT + "/api/sync?pin=" + token;
    }

    /**
     * Generates a QR code as a JavaFX Image (square, given size in pixels).
     * White modules on dark background to match the app theme.
     */
    public Image generateQrImage(String content, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);

        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        // Render: white QR on dark navy background to match app theme
        MatrixToImageConfig config = new MatrixToImageConfig(0xFF_E2E8F0, 0xFF_0F1117);
        BufferedImage awtImage = MatrixToImageWriter.toBufferedImage(matrix, config);

        return convertToFxImage(awtImage);
    }

    /** Converts java.awt.BufferedImage → javafx.scene.image.Image without ImageIO. */
    private Image convertToFxImage(BufferedImage awtImage) {
        int w = awtImage.getWidth();
        int h = awtImage.getHeight();
        WritableImage fxImage = new WritableImage(w, h);
        PixelWriter pw = fxImage.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setArgb(x, y, awtImage.getRGB(x, y));
            }
        }
        return fxImage;
    }
}
