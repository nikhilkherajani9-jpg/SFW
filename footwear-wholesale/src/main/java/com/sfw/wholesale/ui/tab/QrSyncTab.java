package com.sfw.wholesale.ui.tab;

import com.sfw.wholesale.service.SyncService;
import com.sfw.wholesale.ui.component.ConfirmDialog;
import com.sfw.wholesale.util.NetworkUtil;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * Tab 5: QR Sync
 *
 * Pairs the desktop app with the companion Flutter mobile app over a local
 * Windows Mobile Hotspot. No internet, no router required.
 *
 * Flow:
 *  1. User enables Windows Mobile Hotspot in Settings.
 *  2. User enters the hotspot SSID name and clicks "Generate QR".
 *  3. App detects local IP addresses; if multiple, shows a ChoiceDialog.
 *  4. One-time token is generated; HTTP server starts on detected IP:8742.
 *  5. QR displayed — phone scans it to connect and pull data.
 *  6. Token is invalidated after the first successful /data fetch.
 *  7. Click "Generate New QR" to create a fresh token for the next session.
 */
public class QrSyncTab extends BorderPane implements TabShortcutHandler {

    private final SyncService syncSvc = new SyncService();

    private ImageView  qrImageView;
    private Label      statusLabel;
    private Label      ipLabel;
    private TextField  ssidField;
    private Button     generateBtn;
    private Button     stopBtn;
    
    private CheckBox   syncStockCb;
    private CheckBox   syncLrsCb;
    private Label      pinLabel;

    @Override
    public void handleShortcut(javafx.scene.input.KeyEvent event) {
        // Implement tab-specific shortcuts here
    }

    public QrSyncTab() {
        getStyleClass().add("pane-bg");
        buildUI();
    }

    // ── UI Construction ────────────────────────────────────────────────────────

    private void buildUI() {
        // ── Left column: instructions + controls ──────────────────────────────
        VBox leftCol = new VBox(20);
        leftCol.setPadding(new Insets(32, 24, 24, 32));
        leftCol.setMaxWidth(420);
        leftCol.setMinWidth(320);

        Label title = new Label("Mobile Sync");
        title.getStyleClass().add("tab-section-title");

        Label intro = new Label(
                "Sync current Stock, LR, and Creditor data to the companion\n" +
                "mobile app over a local WiFi hotspot.\n\n" +
                "The mobile app can VIEW data only — it can never add,\n" +
                "edit, or delete anything.");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        // Step instructions
        VBox steps = buildStepList();

        // Data to sync options
        Label syncOptionsLbl = new Label("Data to Sync");
        syncOptionsLbl.getStyleClass().add("form-label");
        
        syncStockCb = new CheckBox("Stock Inventory");
        syncStockCb.setSelected(true);
        syncLrsCb = new CheckBox("LR Entries");
        syncLrsCb.setSelected(true);
        
        VBox optionsBox = new VBox(8, syncStockCb, syncLrsCb);
        optionsBox.setPadding(new Insets(0, 0, 10, 0));

        // SSID input
        Label ssidLbl = new Label("Hotspot SSID Name");
        ssidLbl.getStyleClass().add("form-label");
        ssidField = new TextField();
        ssidField.setPromptText("Enter the name of your Windows hotspot...");
        ssidField.getStyleClass().add("styled-text-field");
        ssidField.setMaxWidth(Double.MAX_VALUE);

        // Detected IP display
        ipLabel = new Label("IP: not yet detected");
        ipLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12px; -fx-font-family: monospace;");

        // Status label
        statusLabel = new Label("Server: Not running");
        statusLabel.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");

        // Buttons
        generateBtn = new Button("Generate Pairing QR");
        generateBtn.setId("qrStartBtn");
        generateBtn.getStyleClass().addAll("action-btn","btn-primary");
        generateBtn.setMaxWidth(Double.MAX_VALUE);
        generateBtn.setOnAction(e -> generateQr());

        Button usbBtn = new Button("Enable USB Sync");
        usbBtn.getStyleClass().addAll("action-btn","btn-secondary");
        usbBtn.setMaxWidth(Double.MAX_VALUE);
        usbBtn.setOnAction(e -> startUsbSync());

        Button exportBtn = new Button("Export File for WhatsApp");
        exportBtn.getStyleClass().addAll("action-btn","btn-secondary");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setOnAction(e -> exportForMobile());

        stopBtn = new Button("Stop Server");
        stopBtn.setId("qrClearBtn");
        stopBtn.getStyleClass().addAll("action-btn","btn-danger");
        stopBtn.setMaxWidth(Double.MAX_VALUE);
        stopBtn.setVisible(false);
        stopBtn.setOnAction(e -> stopServer());

        leftCol.getChildren().addAll(
                title, intro, new Separator(), steps,
                syncOptionsLbl, optionsBox,
                ssidLbl, ssidField, ipLabel,
                new Region() {{ setMinHeight(8); }},
                exportBtn, usbBtn, generateBtn, stopBtn, statusLabel
        );

        // ── Right column: QR code display ─────────────────────────────────────
        VBox rightCol = new VBox();
        rightCol.setAlignment(Pos.CENTER);
        rightCol.setPadding(new Insets(32));
        rightCol.getStyleClass().add("qr-panel");

        qrImageView = new ImageView();
        qrImageView.setFitWidth(360);
        qrImageView.setFitHeight(360);
        qrImageView.setPreserveRatio(true);
        qrImageView.setSmooth(false); // keeps QR pixel-sharp

        // Placeholder before QR is generated
        Label qrPlaceholder = new Label("QR code will appear here.\nClick \"Generate Pairing QR\" to start.");
        qrPlaceholder.setTextAlignment(TextAlignment.CENTER);
        qrPlaceholder.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");
        qrPlaceholder.setWrapText(true);
        qrPlaceholder.setMaxWidth(300);

        // Container that shows placeholder OR qr
        StackPane qrContainer = new StackPane(qrPlaceholder, qrImageView);
        qrContainer.setStyle(
                "-fx-background-color: #f8f9fa; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12;");
        qrContainer.setMinSize(380, 380);
        qrContainer.setMaxSize(420, 420);

        qrImageView.visibleProperty().addListener((obs, old, visible) -> {
            qrPlaceholder.setVisible(!visible);
        });
        qrImageView.setVisible(false);

        Label scanHint = new Label("Scan with the SFW companion app");
        scanHint.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");

        Label tokenHint = new Label("⚠ PIN is one-time use — regenerate QR for each sync session.");
        tokenHint.setStyle("-fx-text-fill: #d97706; -fx-font-size: 11px;");
        tokenHint.setWrapText(true);
        tokenHint.setMaxWidth(380);

        pinLabel = new Label("PIN: ----");
        pinLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        pinLabel.setVisible(false);
        qrImageView.visibleProperty().addListener((obs, old, visible) -> {
            pinLabel.setVisible(visible);
        });

        rightCol.getChildren().addAll(qrContainer, scanHint, pinLabel, tokenHint);
        VBox.setVgrow(qrContainer, Priority.ALWAYS);

        // ── Assemble ─────────────────────────────────────────────────────────
        javafx.scene.control.ScrollPane leftScroll = new javafx.scene.control.ScrollPane(leftCol);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        leftScroll.setMinWidth(360);
        leftScroll.setMaxWidth(440);

        HBox mainLayout = new HBox(leftScroll, rightCol);
        HBox.setHgrow(rightCol, Priority.ALWAYS);
        setCenter(mainLayout);
    }

    private VBox buildStepList() {
        VBox steps = new VBox(8);
        steps.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-padding: 16;");
        steps.getChildren().add(stepLabel("1", "Enable Windows Mobile Hotspot in Settings."));
        steps.getChildren().add(stepLabel("2", "Type the hotspot SSID name above."));
        steps.getChildren().add(stepLabel("3", "Click \"Generate Pairing QR\" below."));
        steps.getChildren().add(stepLabel("4", "Scan the QR code with your phone camera."));
        steps.getChildren().add(stepLabel("5", "Open the link, install the app, and enter the PIN."));
        steps.getChildren().add(stepLabel("6", "Data transfers automatically. Done!"));
        return steps;
    }

    private HBox stepLabel(String num, String text) {
        Label numLbl = new Label(num);
        numLbl.setStyle(
                "-fx-background-color: #3b82f6; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-font-size: 11px; " +
                "-fx-min-width: 22; -fx-min-height: 22; " +
                "-fx-background-radius: 11; -fx-alignment: center; -fx-padding: 0 0 1 0;");
        Label txtLbl = new Label(text);
        txtLbl.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px;");
        txtLbl.setWrapText(true);
        HBox row = new HBox(10, numLbl, txtLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void generateQr() {
        String ssid = ssidField.getText().trim();
        if (ssid.isBlank()) {
            ConfirmDialog.showError("Missing SSID",
                    "Please enter the name (SSID) of your Windows Mobile Hotspot.");
            return;
        }

        // Detect local IP addresses
        List<String> addresses = NetworkUtil.getAddressDisplayList();
        if (addresses.isEmpty()) {
            ConfirmDialog.showError("No Network Interface Found",
                    "No suitable local IPv4 address detected.\n\n" +
                    "Please make sure your Windows Mobile Hotspot is turned ON in Settings,\n" +
                    "and that your WiFi adapter is connected and active.");
            return;
        }

        // If multiple addresses, let user choose
        String selectedAddress;
        if (addresses.size() == 1) {
            selectedAddress = addresses.get(0);
        } else {
            ChoiceDialog<String> choice = new ChoiceDialog<>(addresses.get(0), addresses);
            choice.setTitle("Select Hotspot IP Address");
            choice.setHeaderText(null);
            choice.setContentText(
                    "Multiple network interfaces detected.\n" +
                    "Select the one corresponding to your Windows Mobile Hotspot\n" +
                    "(usually shows 'Microsoft Hosted Network' or '192.168.137.x'):");
            var result = choice.showAndWait();
            if (result.isEmpty()) return;
            selectedAddress = result.get();
        }

        String ip = NetworkUtil.extractIp(selectedAddress);

        // Run server start + QR generation off the UI thread
        setStatus("Starting server...", "#d97706");
        generateBtn.setDisable(true);

        // Capture checkbox states on the UI thread before passing to background thread
        boolean doStock = syncStockCb.isSelected();
        boolean doLrs = syncLrsCb.isSelected();

        new Thread(() -> {
            try {
                javafx.scene.image.Image qr = syncSvc.startAndGenerateQr(ssid, ip, doStock, doLrs);
                Platform.runLater(() -> {
                    qrImageView.setImage(qr);
                    qrImageView.setVisible(true);
                    pinLabel.setText("PIN: " + syncSvc.getCurrentToken());
                    ipLabel.setText("Server: " + ip + ":" + syncSvc.getServerPort());
                    setStatus("✓ Server running — scan QR to connect", "#10b981");
                    generateBtn.setText("Generate New QR");
                    generateBtn.setDisable(false);
                    stopBtn.setVisible(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("✗ Error: " + ex.getMessage(), "#ef4444");
                    generateBtn.setDisable(false);
                    ConfirmDialog.showError("Sync Error",
                            "Could not start sync server:\n" + ex.getMessage());
                });
            }
        }, "sync-server-thread").start();
    }

    private void startUsbSync() {
        setStatus("Starting USB Sync...", "#d97706");
        boolean doStock = syncStockCb.isSelected();
        boolean doLrs = syncLrsCb.isSelected();

        new Thread(() -> {
            try {
                // Try executing adb reverse
                String adbPath = "adb";
                java.io.File sdkAdb = new java.io.File(System.getenv("LOCALAPPDATA") + "\\\\Android\\\\Sdk\\\\platform-tools\\\\adb.exe");
                if (sdkAdb.exists()) {
                    adbPath = sdkAdb.getAbsolutePath();
                }
                
                ProcessBuilder pb = new ProcessBuilder(adbPath, "reverse", "tcp:8742", "tcp:8742");
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    java.util.Scanner s = new java.util.Scanner(process.getErrorStream()).useDelimiter("\\A");
                    String error = s.hasNext() ? s.next() : "Unknown adb error";
                    throw new Exception("ADB failed (" + exitCode + "): " + error + "\nMake sure your phone is connected and USB Debugging is ON.");
                }

                syncSvc.startServerForUsb(doStock, doLrs);

                Platform.runLater(() -> {
                    qrImageView.setVisible(false);
                    pinLabel.setVisible(false);
                    ipLabel.setText("Server: 127.0.0.1:8742 (USB Tunnel Active)");
                    setStatus("✓ USB Tunnel Active — Tap 'Sync via USB' on phone", "#10b981");
                    stopBtn.setVisible(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setStatus("✗ USB Error: " + ex.getMessage(), "#ef4444");
                    ConfirmDialog.showError("USB Sync Error", "Could not establish USB connection.\n\n" + ex.getMessage());
                });
            }
        }, "usb-sync-thread").start();
    }

    private void exportForMobile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Sync File for WhatsApp");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SFW Data Files", "*.sfwdata"));
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setInitialFileName("SFW_Sync_" + timestamp + ".sfwdata");
        
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            setStatus("Exporting file...", "#d97706");
            boolean doStock = syncStockCb.isSelected();
            boolean doLrs = syncLrsCb.isSelected();
            
            new Thread(() -> {
                try {
                    byte[] data = syncSvc.generateEncryptedExport(doStock, doLrs);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(data);
                    }
                    Platform.runLater(() -> {
                        setStatus("✓ File exported successfully!", "#10b981");
                        ConfirmDialog.showInfo("Export Complete", "Sync file saved to:\n" + file.getAbsolutePath() + "\n\nYou can now share this file via WhatsApp.");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        setStatus("✗ Export Error: " + ex.getMessage(), "#ef4444");
                        ConfirmDialog.showError("Export Error", "Failed to generate sync file:\n" + ex.getMessage());
                    });
                }
            }, "export-thread").start();
        }
    }

    private void stopServer() {
        syncSvc.stop();
        qrImageView.setVisible(false);
        stopBtn.setVisible(false);
        generateBtn.setText("Generate Pairing QR");
        setStatus("Server stopped.", "#64748b");
        ipLabel.setText("IP: not yet detected");
    }

    private void setStatus(String msg, String color) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px;");
    }

    /** Called when the app closes — ensures the server thread is shut down. */
    public void onAppClose() {
        syncSvc.stop();
    }
}
