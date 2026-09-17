package com.sfw.wholesale;

import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.service.ExportImportService;
import com.sfw.wholesale.ui.component.ConfirmDialog;
import com.sfw.wholesale.ui.tab.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;
import javafx.concurrent.Task;

/**
 * Main application window.
 * Contains the 5-tab TabPane plus a top menu bar for Export/Import.
 * Launched maximised (per spec). Layout is fully responsive (no fixed pixel sizes).
 */
public class MainWindow {

    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());

    private final Stage              stage;
    private final DatabaseManager    db       = DatabaseManager.getInstance();
    private final ExportImportService expImp   = new ExportImportService();

    private QrSyncTab qrSyncTab;
    private TabPane   mainTabPane;

    public MainWindow(Stage stage) {
        this.stage = stage;
    }

    // ── Build & Show ──────────────────────────────────────────────────────────

    public void show() {
        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        
        mainTabPane = buildTabPane();
        root.setCenter(mainTabPane);
        root.getStyleClass().add("root-pane");

        javafx.scene.Scene scene = new javafx.scene.Scene(root);

        // Global shortcut router
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (mainTabPane != null) {
                Tab selected = mainTabPane.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getContent() instanceof TabShortcutHandler handler) {
                    handler.handleShortcut(event);
                }
            }
        });

        // Load dark theme CSS
        try {
            String css = Objects.requireNonNull(
                    getClass().getResource("/css/app.css")).toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            LOG.warning("Could not load app.css: " + e.getMessage());
        }

        // Load window icon (PNG)
        try {
            Image icon = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icons/sfw-logo.png")));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            LOG.warning("Could not load window icon: " + e.getMessage());
        }

        stage.setTitle("SFW Footwear Wholesale");
        stage.setScene(scene);
        stage.setMaximized(true);                   // always launches maximised
        stage.setMinWidth(1100);
        stage.setMinHeight(700);

        // Graceful shutdown on close
        stage.setOnCloseRequest(event -> {
            if (qrSyncTab != null) qrSyncTab.onAppClose();
            db.close(false);   // no VACUUM on close — preserve HDD performance
            Platform.exit();
        });

        stage.show();
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("menu-bar");

        // File menu
        Menu fileMenu = new Menu("File");

        MenuItem exportItem = new MenuItem("📤 Export Backup (CSV)...");
        exportItem.setOnAction(e -> handleExport());

        MenuItem importItem = new MenuItem("📥 Import / Restore...");
        importItem.setOnAction(e -> handleImport());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());

        fileMenu.getItems().addAll(exportItem, importItem, new SeparatorMenuItem(), exitItem);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About SFW Wholesale");
        aboutItem.setOnAction(e -> ConfirmDialog.showInfo("SFW Footwear Wholesale",
                "Version 1.0.0\n\nFootwear Wholesale Management System\n" +
                "Developed for offline, USB-HDD deployment.\n" +
                "Database: SQLite (footwear.db)\n" +
                "All data is stored locally — no cloud, no internet required."));
        helpMenu.getItems().add(aboutItem);

        bar.getMenus().addAll(fileMenu, helpMenu);
        return bar;
    }

    // ── Tab pane ──────────────────────────────────────────────────────────────

    private TabPane buildTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        LrEntryTab lrEntryContent = new LrEntryTab();
        Tab lrTab       = makeTab("📋 LR Entry",       lrEntryContent);
        lrTab.setId("tabLrEntry");
        
        LrListTab lrListContent = new LrListTab(entry -> {
            tabPane.getSelectionModel().select(lrTab);
            lrEntryContent.loadLrForEditing(entry);
        });
        Tab lrListTab   = makeTab("📄 All LRs",       lrListContent);
        lrListTab.setId("tabLrList");
        
        Tab stockTab    = makeTab("📦 Stock",           new StockTab());
        stockTab.setId("tabStock");

        qrSyncTab = new QrSyncTab();
        Tab transferTab = makeTab("🔄 GD Transfers",   new GdTransferTab());
        transferTab.setId("tabGdTransfer");
        
        Tab syncTab     = makeTab("📡 QR Sync",         qrSyncTab);
        syncTab.setId("tabQrSync");

        tabPane.getTabs().addAll(lrTab, lrListTab, stockTab, transferTab, syncTab);
        return tabPane;
    }

    private Tab makeTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.getStyleClass().add("main-tab");
        return tab;
    }

    // ── Export / Import handlers ──────────────────────────────────────────────

    // BUG-01 FIX: export runs on a daemon background thread — FXAT never blocked
    private void handleExport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder to save backup CSV files");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return expImp.export(dir.toPath());
            }
        };
        task.setOnSucceeded(e -> ConfirmDialog.showInfo("Export Complete",
                "Backup saved to:\n" + task.getValue().toAbsolutePath() +
                "\n\nThis folder contains CSV files — share with your accountant or\n" +
                "use it to restore data if the database is ever corrupted."));
        task.setOnFailed(e -> {
            ConfirmDialog.showError("Export Failed", "Could not export data:\n" +
                    task.getException().getMessage());
            LOG.severe("Export error: " + task.getException().getMessage());
        });
        Thread t = new Thread(task, "export-thread");
        t.setDaemon(true);
        t.start();
    }

    // BUG-02 FIX: import runs on a daemon background thread — FXAT never blocked
    private void handleImport() {
        boolean confirmed = ConfirmDialog.show(
                "Import / Restore",
                "⚠ WARNING: Importing will REPLACE ALL current data in the database.\n\n" +
                "Only do this to recover from a corrupted or lost database.\n\n" +
                "Select the folder exported by a previous backup (SFW_Export_*).\n\n" +
                "Are you sure you want to continue?");
        if (!confirmed) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select SFW_Export_* folder to restore from");
        File dir = chooser.showDialog(stage);
        if (dir == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                expImp.importFromFolder(dir.toPath());
                return null;
            }
        };
        task.setOnSucceeded(e -> ConfirmDialog.showInfo("Import Complete",
                "Data restored successfully from:\n" + dir.getAbsolutePath()));
        task.setOnFailed(e -> {
            ConfirmDialog.showError("Import Failed",
                    "Could not restore data:\n" + task.getException().getMessage() +
                    "\n\nYour existing data has NOT been modified (import was aborted).");
            LOG.severe("Import error: " + task.getException().getMessage());
        });
        Thread t = new Thread(task, "import-thread");
        t.setDaemon(true);
        t.start();
    }
}
