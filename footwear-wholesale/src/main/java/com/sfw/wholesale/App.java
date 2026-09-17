package com.sfw.wholesale;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.license.LicenseClient;
import com.sfw.wholesale.license.LicenseManager;
import com.sfw.wholesale.ui.ActivationScreen;
import com.sfw.wholesale.ui.component.ConfirmDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application entry point.
 *
 * Startup sequence:
 *  1. Show a splash screen (SFW logo + progress bar) while loading runs in background.
 *  2. Open the SQLite database (creates it if first run).
 *  3. Load all in-memory caches (LR list, stock, transports).
 *  4. Dismiss splash and show MainWindow (maximised).
 *
 * This pattern keeps the splash visible while the HDD reads happen, so the user
 * sees immediate feedback rather than a blank window.
 */
public class App extends Application {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    @Override
    public void start(Stage primaryStage) {
        Stage splash = buildSplash();
        splash.show();

        // BUG-07: Install global handler so no thread ever dies silently
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOG.log(Level.SEVERE, "Uncaught exception on thread: " + thread.getName(), throwable);
            Platform.runLater(() -> ConfirmDialog.showError("Unexpected Error",
                    "An unexpected error occurred on thread '" + thread.getName() + "':\n"
                    + throwable.getMessage()
                    + "\n\nPlease restart the application if behaviour is abnormal."));
        });

        // Licensing Flow
        Thread licenseChecker = new Thread(() -> {
            LicenseManager.LocalLicense license = LicenseManager.getLocalLicense();

            if (license == null) {
                // No valid license -> Show Activation Screen
                Platform.runLater(() -> {
                    splash.close();
                    new ActivationScreen(() -> {
                        // On successful activation, resume normal startup
                        Stage newSplash = buildSplash();
                        newSplash.show();
                        startDbLoader(newSplash, primaryStage);
                    }).show();
                });
                return;
            }

            if (LicenseManager.isWithinGracePeriod(license)) {
                // Within 4 days offline grace period -> Let app open immediately
                startDbLoader(splash, primaryStage);
                
                // Silent background verification to refresh timestamp or catch revocations
                verifyLicenseSilently(license, true, splash, primaryStage);
            } else {
                // Outside grace period -> Force online check
                Platform.runLater(() -> {
                    Label subLbl = (Label) splash.getScene().getRoot().getChildrenUnmodifiable().get(2);
                    subLbl.setText("Verifying License Online (Grace Period Expired)...");
                });

                LicenseClient.VerificationResult result = LicenseClient.verifyLicense(license.productKey);
                if (result.status == LicenseClient.LicenseStatus.VALID) {
                    LicenseManager.saveLocalLicense(license.productKey);
                    startDbLoader(splash, primaryStage);
                } else if (result.status == LicenseClient.LicenseStatus.NETWORK_ERROR) {
                    Platform.runLater(() -> {
                        splash.close();
                        ConfirmDialog.showError("Activation Required", 
                            "It has been over 4 days since the last license check.\n" +
                            "Please connect to the internet to verify your license.");
                        Platform.exit();
                    });
                } else {
                    // Revoked or bound to other PC
                    LicenseManager.clearLocalLicense();
                    requireActivation(primaryStage, result.message);
                }
            }
        }, "license-checker");
        
        licenseChecker.setDaemon(true);
        licenseChecker.start();
    }

    private void verifyLicenseSilently(LicenseManager.LocalLicense currentLicense, boolean isStartup, Stage splash, Stage primaryStage) {
        Thread t = new Thread(() -> {
            LicenseClient.VerificationResult result = LicenseClient.verifyLicense(currentLicense.productKey);
            
            if (result.status == LicenseClient.LicenseStatus.VALID) {
                LicenseManager.saveLocalLicense(currentLicense.productKey);
                LOG.info("Background license check passed. Timestamp refreshed.");
            } else if (result.status == LicenseClient.LicenseStatus.REVOKED || result.status == LicenseClient.LicenseStatus.BOUND_TO_OTHER_PC) {
                LicenseManager.clearLocalLicense();
                requireActivation(primaryStage, "This product key has been revoked or bound to another PC.");
            } else if (result.status == LicenseClient.LicenseStatus.NETWORK_ERROR) {
                if (isStartup) {
                    int hoursRemaining = LicenseManager.getRemainingGracePeriodHours(currentLicense);
                    Platform.runLater(() -> {
                        ConfirmDialog.showError("License Verification Failed", 
                            "The PC is not connected to the internet.\n\n" +
                            "You can use the software offline for " + hoursRemaining + " more hours before re-verification is strictly required.");
                    });
                }
            }
        }, "startup-license-checker");
        t.setDaemon(true);
        t.start();
    }

    private void requireActivation(Stage primaryStage, String reason) {
        Platform.runLater(() -> {
            // Close all currently open windows (including splash or MainWindow)
            List<Window> openWindows = new ArrayList<>(Window.getWindows());
            for (Window w : openWindows) {
                if (w instanceof Stage) {
                    ((Stage) w).close();
                }
            }

            if (reason != null && !reason.isEmpty()) {
                ConfirmDialog.showError("License Revoked", reason);
            }

            new ActivationScreen(() -> {
                // On successful activation, resume normal startup
                Stage newSplash = buildSplash();
                newSplash.show();
                startDbLoader(newSplash, primaryStage);
            }).show();
        });
    }

    private void startDbLoader(Stage splash, Stage primaryStage) {
        Thread loader = new Thread(() -> {
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                db.open();
                DataCache.getInstance().loadAll(db.getConnection());
                Thread.sleep(600); // brief pause so splash is visible

                Platform.runLater(() -> {
                    splash.close();
                    MainWindow window = new MainWindow(primaryStage);
                    window.show();
                });

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Startup failed", e);
                Platform.runLater(() -> {
                    splash.close();
                    ConfirmDialog.showError("Startup Error",
                            "Could not initialise the database:\n" + e.getMessage() +
                            "\n\nPlease check that the application folder is writable\n" +
                            "and that footwear.db is not open in another program.");
                    Platform.exit();
                });
            }
        }, "startup-loader");

        loader.setDaemon(true);
        loader.start();
    }

    /** Builds a borderless splash screen with the SFW logo. */
    private Stage buildSplash() {
        Stage splash = new Stage(StageStyle.UNDECORATED);

        VBox root = new VBox(24);
        root.setAlignment(Pos.CENTER);
        root.setStyle(
                "-fx-background-color: #f8f9fa; " +
                "-fx-border-color: #3b82f6; " +
                "-fx-border-width: 1.5; " +
                "-fx-border-radius: 16; " +
                "-fx-background-radius: 16;");
        root.setPrefSize(420, 320);

        // Logo
        ImageView logoView = new ImageView();
        logoView.setFitWidth(120);
        logoView.setFitHeight(120);
        logoView.setPreserveRatio(true);
        logoView.setSmooth(true);
        try {
            Image logo = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icons/sfw-logo.png")));
            logoView.setImage(logo);
        } catch (Exception e) {
            // Logo file missing — continue without it
        }

        Label titleLbl = new Label("SFW Footwear Wholesale");
        titleLbl.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label subLbl = new Label("Loading data, please wait...");
        subLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        ProgressBar progress = new ProgressBar();
        progress.setProgress(-1); // indeterminate
        progress.setPrefWidth(260);
        progress.setStyle("-fx-accent: #3b82f6;");

        Label version = new Label("v1.0.0  ·  Local SQLite  ·  Fully Offline");
        version.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        root.getChildren().addAll(logoView, titleLbl, subLbl, progress, version);

        Scene scene = new Scene(root, 420, 320);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource("/css/app.css")).toExternalForm());
        } catch (Exception ignored) {}

        splash.setScene(scene);
        splash.initStyle(StageStyle.TRANSPARENT);
        splash.centerOnScreen();

        // Set icon for taskbar (even on splash)
        try {
            splash.getIcons().add(new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icons/sfw-logo.png"))));
        } catch (Exception ignored) {}

        return splash;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
