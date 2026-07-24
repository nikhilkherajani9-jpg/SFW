package com.sfw.wholesale;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
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

        // Load database and caches on a background thread
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
