package com.sfw.wholesale.ui.tab;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.model.*;
import com.sfw.wholesale.service.*;
import com.sfw.wholesale.ui.component.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tab 2: Stock
 *
 * Shows current stock across all 6 warehouses. Shop never appears here.
 *
 * Views:
 *   Flat    — every stock row individually (as stored in DB)
 *   Grouped — sum of cartons per product+size+location (computed in-memory)
 *
 * Filters: text search on product name, location checkboxes, size text filter.
 * Actions: Transfer Stock (switches to GD Transfers tab), Add Stock Manually.
 */
public class StockTab extends BorderPane implements TabShortcutHandler {

    private static final Logger LOG   = Logger.getLogger(StockTab.class.getName());
    private static final String[] WAREHOUSES = {"G1","G2","G3","G4","G5","RK2","Shop"};

    private final DataCache   cache    = DataCache.getInstance();
    private final StockService stockSvc = new StockService();
    private final DatabaseManager db   = DatabaseManager.getInstance();

    private TableView<StockRow>          table;
    private TextField                    searchField;
    private final Set<String>            selectedLocations = new HashSet<>(Arrays.asList(WAREHOUSES));

    // The list shown in the table (filtered view of cache.stockRows)
    private final ObservableList<StockRow> displayRows = FXCollections.observableArrayList();

    @Override
    public void handleShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.F) {
            if (searchField != null) {
                searchField.requestFocus();
                searchField.selectAll();
            }
            event.consume();
        }
    }

    public StockTab() {
        getStyleClass().add("pane-bg");
        setCenter(buildTable());
        setTop(buildTopBar());
        setPadding(new Insets(20));

        // Auto-refresh display when stock cache changes
        cache.getStockRows().addListener((ListChangeListener<StockRow>) c -> refreshDisplay());
        refreshDisplay();
    }

    // ── Top bar ────────────────────────────────────────────────────────────────

    private VBox buildTopBar() {
        VBox bar = new VBox(12);
        bar.setPadding(new Insets(0, 0, 16, 0));

        Label title = new Label("Stock");
        title.getStyleClass().add("tab-section-title");

        // Search + Size filter + View toggle
        searchField = new TextField();
        searchField.setId("stockSearchField");
        searchField.setPromptText("Search product name...");
        searchField.getStyleClass().add("styled-text-field");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs,o,n) -> refreshDisplay());

        // Location filter checkboxes
        HBox locationBox = new HBox(12);
        locationBox.setAlignment(Pos.CENTER_LEFT);
        for (String loc : WAREHOUSES) {
            CheckBox cb = new CheckBox(loc);
            cb.setSelected(!loc.equals("Shop")); // Check all except Shop by default
            cb.getStyleClass().add("location-checkbox");
            cb.selectedProperty().addListener((obs, o, selected) -> {
                if (selected) selectedLocations.add(loc);
                else          selectedLocations.remove(loc);
                refreshDisplay();
            });
            locationBox.getChildren().add(cb);
            if (!loc.equals("Shop")) {
                selectedLocations.add(loc);
            } else {
                selectedLocations.remove("Shop"); // Ensure Shop is not in default set
            }
        }
        Label locLabel = new Label("Locations:");
        locLabel.getStyleClass().add("form-label");

        // Action buttons
        Button transferBtn = buildActionBtn("→ Transfer", "btn-primary", e -> openTransferDialog());
        transferBtn.setId("transferBtn");
        Button manualBtn   = buildActionBtn("+ Manual Add", "btn-secondary", e -> openManualAddDialog());
        manualBtn.setId("manualAddBtn");
        Button importBtn   = buildActionBtn("Import Legacy (.xls)", "btn-secondary", e -> importLegacyData());
        importBtn.setId("importLegacyBtn");
        Button deleteStockBtn = buildActionBtn("🗑 Delete", "btn-danger", e -> deleteSelectedStock());
        deleteStockBtn.setId("deleteStockBtn");

        HBox searchRow    = new HBox(12, searchField, new Separator(javafx.geometry.Orientation.VERTICAL), 
                new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, transferBtn, manualBtn, deleteStockBtn, importBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(LrEntryTab.class);
        double savedFontSize = prefs.getDouble("tableZoomFontSize", 14.0);

        Slider rowHeightSlider = new Slider(12, 36, savedFontSize);
        rowHeightSlider.setShowTickMarks(true);
        rowHeightSlider.setMajorTickUnit(4);
        rowHeightSlider.setPrefWidth(120);

        rowHeightSlider.valueProperty().addListener((obs, old, val) -> {
            double fontSize = val.doubleValue();
            table.setStyle("-fx-font-size: " + fontSize + "px;");
            table.setFixedCellSize((fontSize * 1.5) + 16);
            prefs.putDouble("tableZoomFontSize", fontSize);
        });
        
        table.setStyle("-fx-font-size: " + savedFontSize + "px;");
        table.setFixedCellSize((savedFontSize * 1.5) + 16);

        Label zoomLabel = new Label("Zoom: ");
        zoomLabel.getStyleClass().add("form-label");
        HBox zoomBox = new HBox(5, zoomLabel, rowHeightSlider);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox locationRow = new HBox(8, locLabel, locationBox, spacer, zoomBox);
        locationRow.setAlignment(Pos.CENTER_LEFT);

        bar.getChildren().addAll(title, searchRow, locationRow);
        return bar;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TableView<StockRow> buildTable() {
        table = new TableView<>(displayRows);
        table.setId("stockTable");
        table.setEditable(false);
        table.getStyleClass().add("stock-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No stock found for current filters."));

        TableColumn<StockRow,String>  colDate    = new TableColumn<>("Date");
        colDate.setCellValueFactory(c -> {
            java.time.LocalDate d = c.getValue().getReceiveDate();
            return new javafx.beans.property.SimpleStringProperty(d != null ? d.toString() : "");
        });
        
        TableColumn<StockRow,String>  colProduct = strCol("Product Name", "productName");
        TableColumn<StockRow,String>  colLoc     = strCol("Location",     "location");
        TableColumn<StockRow,Number>  colCtns    = numCol("Cartons",      "cartons");
        TableColumn<StockRow,Number>  colPpc     = numCol("Prs/Ctn",      "pairsPerCarton");
        TableColumn<StockRow,Number>  colTotal   = numCol("Total Pairs",  "totalPairs");
        TableColumn<StockRow,String>  colSource  = strCol("LR Source",    "lrSource");

        colProduct.setMinWidth(160);
        colTotal.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");

        table.getColumns().setAll(colDate, colProduct, colLoc, colCtns, colPpc, colTotal, colSource);
        
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<StockRow> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    StockRow rowData = row.getItem();
                    openLedgerViewer(rowData.getProductName(), rowData.getLocation());
                }
            });
            return row;
        });
        
        return table;
    }

    // ── Filtering & display ────────────────────────────────────────────────────

    private void refreshDisplay() {
        String search  = searchField != null ? searchField.getText().toLowerCase() : "";

        List<StockRow> filtered = cache.getStockRows().stream()
                .filter(r -> search.isBlank() || r.getProductName().toLowerCase().contains(search))
                .filter(r -> selectedLocations.contains(r.getLocation()))
                .collect(Collectors.toList());

        displayRows.setAll(groupRows(filtered));
    }

    /**
     * Groups rows by product+size+location, summing cartons.
     * totalPairs is re-calculated from the summed cartons × pairsPerCarton.
     */
    private List<StockRow> groupRows(List<StockRow> rows) {
        Map<String, StockRow> grouped = new LinkedHashMap<>();
        for (StockRow r : rows) {
            String prod = r.getProductName() == null ? "" : r.getProductName().trim().toUpperCase();
            String loc = r.getLocation() == null ? "" : r.getLocation().trim().toUpperCase();
            String key = prod + "|" + loc; // Removed pairsPerCarton from grouping key
            
            if (grouped.containsKey(key)) {
                StockRow existing = grouped.get(key);
                existing.setCartons(existing.getCartons() + r.getCartons());
                existing.setTotalPairs(existing.getTotalPairs() + r.getTotalPairs());
                
                // Set Prs/Ctn to 0 in grouped view to denote "Mixed/Aggregate"
                existing.setPairsPerCarton(0);
                
                // Merge LR sources
                String currentSource = existing.getLrSource() == null ? "" : existing.getLrSource();
                String newSource = r.getLrSource();
                if (newSource != null && !newSource.isBlank() && !currentSource.contains(newSource)) {
                    existing.setLrSource(currentSource.isBlank() ? newSource : currentSource + ", " + newSource);
                }
                
                // Merge date (take most recent)
                if (r.getReceiveDate() != null) {
                    if (existing.getReceiveDate() == null || r.getReceiveDate().isAfter(existing.getReceiveDate())) {
                        existing.setReceiveDate(r.getReceiveDate());
                    }
                }
            } else {
                // Clone to avoid mutating the cached row
                StockRow clone = new StockRow(-1,
                        r.getProductName(), r.getLocation(),
                        r.getCartons(), r.getPairsPerCarton(), r.getLrSource(), r.getReceiveDate());
                // Force explicit total pairs in case it gets grouped later
                clone.setTotalPairs(r.getTotalPairs()); 
                grouped.put(key, clone);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private void openTransferDialog() {
        StockRow selected = table.getSelectionModel().getSelectedItem();
        // This will be handled by the parent (MainWindow) switching to GD Transfers tab.
        // For now, show a hint.
        if (selected == null) {
            ConfirmDialog.showInfo("Transfer",
                    "Select a stock row first, then click Transfer.\n" +
                    "You can also go directly to the GD Transfers tab.");
            return;
        }
        ConfirmDialog.showInfo("Transfer",
                "Go to the GD Transfers tab and fill in:\n" +
                "Product: " + selected.getProductName() +
                "\nFrom: " + selected.getLocation());
    }

    private void openManualAddDialog() {
        // Build a simple dialog form
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Add Stock Manually");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.setPadding(new Insets(20));

        TextField productField = styledTF("Product Name *");
        TextField cartonsField = styledTF("Cartons *");
        cartonsField.setPromptText("Number of cartons to add");
        TextField ppcField     = styledTF("Pairs per Carton *");
        ComboBox<String> locCombo = new ComboBox<>(FXCollections.observableArrayList(WAREHOUSES));
        locCombo.setMaxWidth(Double.MAX_VALUE);
        locCombo.setValue("G1");
        locCombo.getStyleClass().add("styled-combo");

        form.add(formLabel("Product Name *"), 0, 0); form.add(productField, 1, 0);
        form.add(formLabel("Cartons *"),      0, 1); form.add(cartonsField, 1, 1);
        form.add(formLabel("Pairs/Carton *"), 0, 2); form.add(ppcField,     1, 2);
        form.add(formLabel("Location *"),     0, 3); form.add(locCombo,     1, 3);

        pane.setContent(form);

        ButtonType addBtn    = new ButtonType("Add Stock", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel",    ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(addBtn, cancelBtn);

        styleDialogBtn(pane, addBtn, "#3b82f6", "white");
        styleDialogBtn(pane, cancelBtn, "#e2e8f0", "#334155");

        dialog.setResultConverter(btn -> btn == addBtn);
        dialog.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            try {
                String product  = productField.getText().trim();
                int    cartons  = Integer.parseInt(cartonsField.getText().trim());
                int    ppc      = Integer.parseInt(ppcField.getText().trim());
                String location = locCombo.getValue();

                if (product.isBlank())
                    throw new IllegalArgumentException("Product Name is required.");
                if (cartons <= 0)
                    throw new IllegalArgumentException("Cartons must be > 0.");
                if (ppc <= 0)
                    throw new IllegalArgumentException("Pairs per carton must be > 0.");

                db.inTransaction(conn -> stockSvc.addManualStock(
                        conn, product, location, cartons, ppc, "Manual addition"));
                cache.refreshStock(db.getConnection());
                ConfirmDialog.showInfo("Stock Added",
                        cartons + " cartons of " + product +
                        " added to " + location + ".");
            } catch (NumberFormatException ex) {
                ConfirmDialog.showError("Invalid Input", "Cartons and Pairs must be whole numbers.");
            } catch (IllegalArgumentException ex) {
                ConfirmDialog.showError("Validation Error", ex.getMessage());
            } catch (Exception ex) {
                ConfirmDialog.showError("Error", "Failed to add stock: " + ex.getMessage());
            }
        });
    }

    private void importLegacyData() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Folder with Legacy .xls files");
        java.io.File dir = chooser.showDialog(this.getScene().getWindow());
        if (dir == null) return;

        com.sfw.wholesale.service.LegacyImportService importSvc = new com.sfw.wholesale.service.LegacyImportService();
        java.util.List<com.sfw.wholesale.service.LegacyImportService.ParsedLegacyData> parsedData = importSvc.scanFiles(dir);
        
        if (parsedData.isEmpty()) {
            com.sfw.wholesale.ui.component.ConfirmDialog.showInfo("Import Completed", "No valid files found with balance >= 1.");
            return;
        }

        java.util.List<com.sfw.wholesale.service.LegacyImportService.ParsedLegacyData> invalid = importSvc.getInvalidLocations(parsedData);
        if (!invalid.isEmpty()) {
            com.sfw.wholesale.ui.component.LocationMappingDialog mapDialog = new com.sfw.wholesale.ui.component.LocationMappingDialog(invalid);
            if (!mapDialog.showAndAwaitProceed()) {
                return; // User cancelled
            }
        }

        try {
            importSvc.importData(parsedData, stockSvc, db);
            cache.refreshStock(db.getConnection());
            refreshDisplay();
            com.sfw.wholesale.ui.component.ConfirmDialog.showInfo("Import Success", "Successfully imported " + parsedData.size() + " stock items!");
        } catch (Exception ex) {
            com.sfw.wholesale.ui.component.ConfirmDialog.showError("Import Failed", "Error importing data: " + ex.getMessage());
        }
    }

    private void deleteSelectedStock() {
        StockRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ConfirmDialog.showError("No Selection",
                    "Please select a stock row to delete first.");
            return;
        }
        // Count actual underlying batches (display rows are grouped clones with id=-1)
        long batchCount = cache.getStockRows().stream()
                .filter(r -> r.getProductName().equalsIgnoreCase(selected.getProductName())
                          && r.getLocation().equalsIgnoreCase(selected.getLocation()))
                .count();

        boolean confirmed = ConfirmDialog.show("Delete Stock",
                "Delete ALL stock for:\n" +
                "Product: " + selected.getProductName() + "\n" +
                "Location: " + selected.getLocation() + "\n" +
                "Cartons: " + selected.getCartons() + " (" + batchCount + " batch" + (batchCount == 1 ? "" : "es") + ")" +
                "\n\nThis will also remove all ledger history for this product+location.\n" +
                "This action CANNOT be undone. Are you sure?");
        if (!confirmed) return;

        try {
            db.inTransaction(conn -> stockSvc.deleteStockByProductAndLocation(
                    conn, selected.getProductName(), selected.getLocation()));
            cache.refreshStock(db.getConnection());
            refreshDisplay();
            ConfirmDialog.showInfo("Deleted",
                    selected.getProductName() + " @ " + selected.getLocation() +
                    " has been removed from stock.");
        } catch (Exception ex) {
            ConfirmDialog.showError("Delete Error",
                    "Could not delete stock: " + ex.getMessage());
            LOG.severe("Stock delete error: " + ex.getMessage());
        }
    }

    // ── Column factories ──────────────────────────────────────────────────────

    private TableColumn<StockRow,String> strCol(String title, String property) {
        TableColumn<StockRow,String> col = new TableColumn<>(title);
        col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>(property));
        return col;
    }

    private TableColumn<StockRow,Number> numCol(String title, String property) {
        TableColumn<StockRow,Number> col = new TableColumn<>(title);
        col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>(property));
        col.setStyle("-fx-alignment: CENTER-RIGHT;");
        return col;
    }

    // ── Widget helpers ─────────────────────────────────────────────────────────

    private Button buildActionBtn(String text, String css,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("action-btn", css);
        btn.setOnAction(handler);
        return btn;
    }

    private TextField styledTF(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("styled-text-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label formLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        lbl.setMinWidth(130);
        return lbl;
    }

    private void styleDialogBtn(DialogPane pane, ButtonType type, String bgColor, String fgColor) {
        Button btn = (Button) pane.lookupButton(type);
        if (btn != null) btn.setStyle(
                "-fx-background-color: " + bgColor + "; -fx-text-fill: " + fgColor + "; " +
                "-fx-background-radius: 6; -fx-cursor: hand;");
    }

    // ── Ledger Viewer ─────────────────────────────────────────────────────────
    
    private void openLedgerViewer(String product, String location) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.setTitle("Stock Ledger - " + product + " (" + location + ")");
        
        TableView<com.sfw.wholesale.model.LedgerEntry> ledgerTable = new TableView<>();
        ledgerTable.setEditable(false);
        ledgerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ledgerTable.getStyleClass().add("stock-table");
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTransactionDate().toString()));
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, Number> colInward = new TableColumn<>("Inward");
        colInward.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("inwardCartons"));
        colInward.setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: green; -fx-font-weight: bold;");
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, Number> colOutward = new TableColumn<>("Outward");
        colOutward.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("outwardCartons"));
        colOutward.setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: red; -fx-font-weight: bold;");
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, Number> colBalance = new TableColumn<>("Balance");
        colBalance.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("balanceCartons"));
        colBalance.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1e88e5;");
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, Number> colPpc = new TableColumn<>("Prs/Ctn");
        colPpc.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("pairsPerCarton"));
        colPpc.setStyle("-fx-alignment: CENTER-RIGHT;");
        
        TableColumn<com.sfw.wholesale.model.LedgerEntry, String> colSource = new TableColumn<>("LR Source");
        colSource.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("lrSource"));
        
        ledgerTable.getColumns().setAll(Arrays.asList(colDate, colInward, colOutward, colBalance, colPpc, colSource));
        
        // Fetch data
        try {
            db.inTransaction(conn -> {
                List<com.sfw.wholesale.model.LedgerEntry> entries = stockSvc.getLedgerEntries(conn, product, location);
                ledgerTable.setItems(FXCollections.observableArrayList(entries));
            });
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.SEVERE, "Failed to load ledger", e);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText("Could not load ledger: " + e.getMessage());
            alert.showAndWait();
        }
        
        VBox vbox = new VBox(10, ledgerTable);
        vbox.setPadding(new Insets(20));
        VBox.setVgrow(ledgerTable, Priority.ALWAYS);
        vbox.getStyleClass().add("pane-bg");
        
        javafx.scene.Scene scene = new javafx.scene.Scene(vbox, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }
}
