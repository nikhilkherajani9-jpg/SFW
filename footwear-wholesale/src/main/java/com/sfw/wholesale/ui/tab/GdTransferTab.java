package com.sfw.wholesale.ui.tab;

import javafx.scene.control.cell.TextFieldTableCell;
import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.model.GdTransfer;
import com.sfw.wholesale.service.GdTransferService;
import com.sfw.wholesale.ui.component.ConfirmDialog;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.converter.DefaultStringConverter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Tab 3: GD Transfers
 *
 * Keyboard-driven workflow (per spec):
 * Tab / Shift+Tab → move between cells in a row
 * Enter → save current row, move to next
 * Spacebar → toggle Done checkbox on focused row
 *
 * Done checkbox logic:
 * Unchecked → Checked: apply stock movement immediately
 * Checked → Unchecked: confirm dialog → if confirmed, reverse stock movement
 *
 * Shop transfer rule:
 * Prev Location dropdown: warehouses only (G1-G5, RK2)
 * Updated Location dropdown: warehouses + Shop
 * If destination = Shop: only subtract from source; no stock row created.
 */
public class GdTransferTab extends BorderPane implements TabShortcutHandler {

    private static final Logger LOG = Logger.getLogger(GdTransferTab.class.getName());
    private static final String[] WAREHOUSES = { "G1", "G2", "G3", "G4", "G5", "RK2" };
    private static final String[] DESTINATIONS = { "G1", "G2", "G3", "G4", "G5", "RK2", "Shop" };

    private final DataCache cache = DataCache.getInstance();
    private final GdTransferService svc = new GdTransferService();
    private final com.sfw.wholesale.service.StockService stockSvc = new com.sfw.wholesale.service.StockService();

    private TableView<GdTransfer> table;
    private final ObservableList<GdTransfer> displayList = FXCollections.observableArrayList();

    // Filter state
    private TextField searchField;
    /** Two-step Enter on Qty: first Enter saves+stays, second Enter creates new row. Tracks the row index of the last Qty commit. */
    private int lastEnterRowIdx = -1;

    @Override
    public void handleShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.T) {
            addNewTransferRow();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.S) {
            saveAllRemainingTransfers();
            event.consume();
        }
    }

    public GdTransferTab() {
        getStyleClass().add("pane-bg");
        setCenter(buildTable()); // build table first so buildTopBar() can reference it for zoom
        setTop(buildTopBar());
        setPadding(new Insets(20));

        cache.getTransferList().addListener(
                (ListChangeListener<GdTransfer>) c -> refreshDisplay());
        refreshDisplay();
    }

    // ── Top bar ────────────────────────────────────────────────────────────────

    private VBox buildTopBar() {
        VBox bar = new VBox(12);
        bar.setPadding(new Insets(0, 0, 16, 0));

        // Title row: "GD Transfers — Today: dd-MM-yyyy"
        String todayStr = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        Label title = new Label("GD Transfers  —  Today: " + todayStr);
        title.getStyleClass().add("tab-section-title");

        Button addBtn = new Button("+ New Transfer");
        addBtn.setId("gdAddRowBtn");
        addBtn.getStyleClass().addAll("action-btn", "btn-primary");
        addBtn.setOnAction(e -> addNewTransferRow());

        Button deleteBtn = new Button("✕ Delete Selected");
        deleteBtn.getStyleClass().addAll("action-btn", "btn-danger");
        deleteBtn.setOnAction(e -> deleteSelected());

        Label hint = new Label("Keyboard: Tab=next cell  Enter=save row  Ctrl+T=New Transfer  Ctrl+S=Save All  |  Older transfers → \"Previous Transfers\"");
        hint.getStyleClass().add("hint-label");

        searchField = new TextField();
        searchField.setPromptText("Search today's product...");
        searchField.getStyleClass().add("styled-text-field");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, o, n) -> refreshDisplay());

        HBox controls = new HBox(12, addBtn, deleteBtn,
                new Separator(Orientation.VERTICAL),
                searchField,
                new Region() {
                    {
                        HBox.setHgrow(this, Priority.ALWAYS);
                    }
                });
        controls.setAlignment(Pos.CENTER_LEFT);

        // ── Zoom slider + Previous Transfers button ────────────────────────────────
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
        // Apply saved zoom immediately
        table.setStyle("-fx-font-size: " + savedFontSize + "px;");
        table.setFixedCellSize((savedFontSize * 1.5) + 16);

        Label zoomLabel = new Label("Zoom: ");
        zoomLabel.getStyleClass().add("form-label");
        HBox zoomBox = new HBox(5, zoomLabel, rowHeightSlider);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);

        Button historyBtn = new Button("📋 Previous Transfers");
        historyBtn.setId("gdHistoryBtn");
        historyBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        historyBtn.setOnAction(e -> new TransferHistoryWindow(cache.getTransferList()).show());

        HBox secondRow = new HBox(12, historyBtn,
                new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                zoomBox);
        secondRow.setAlignment(Pos.CENTER_LEFT);

        bar.getChildren().addAll(title, controls, secondRow, hint);
        return bar;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private TableView<GdTransfer> buildTable() {
        table = new TableView<>(displayList);
        table.setId("gdTable");
        table.setEditable(true);
        table.getStyleClass().add("stock-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transfers today. Click \"+ New Transfer\" to add one, or open \"Previous Transfers\" to view older records."));

        // ── Columns ────────────────────────────────────────────────────────────
        TableColumn<GdTransfer, String> colDate = textEditCol("Date");
        colDate.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getTransferDate();
            return new javafx.beans.property.SimpleStringProperty(d != null ? d.toString() : "");
        });
        colDate.setOnEditCommit(e -> {
            try {
                e.getRowValue().setTransferDate(LocalDate.parse(e.getNewValue()));
            } catch (Exception ignored) {
            }
            persistRow(e.getRowValue(), false);
            if (Boolean.TRUE.equals(table.getProperties().get("enterPressed"))) {
                table.getProperties().put("enterPressed", false);
                javafx.application.Platform.runLater(() -> 
                    table.edit(e.getTablePosition().getRow(), table.getColumns().get(1))
                );
            }
        });

        TableColumn<GdTransfer, String> colProduct = new TableColumn<>("Product Name");
        colProduct.setMinWidth(160);
        colProduct.setCellValueFactory(c -> c.getValue().productNameProperty());
        colProduct.setEditable(true);
        colProduct.setCellFactory(col -> new TableCell<>() {
            private com.sfw.wholesale.ui.component.AutoSuggestTextField textField;
            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (textField == null) {
                        textField = new com.sfw.wholesale.ui.component.AutoSuggestTextField(cache.getProductSuggestions());
                        textField.getStyleClass().add("styled-text-field");
                        textField.setAutoSelectSingleOptionOnEnter(true);
                        
                        textField.setOnSuggestionSelected(selected -> {
                            int idx = selected.lastIndexOf(" (");
                            if (idx != -1 && selected.endsWith(")")) {
                                String prod = selected.substring(0, idx);
                                String loc = selected.substring(idx + 2, selected.length() - 1);
                                textField.setTextSilent(prod);
                                com.sfw.wholesale.model.GdTransfer row = getTableRow().getItem();
                                if (row != null) {
                                    row.setPrevLocation(loc);
                                }
                            }
                        });

                        textField.setOnAction(e -> commitEdit(textField.getText()));
                        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                            if (!isNowFocused && isEditing()) commitEdit(textField.getText());
                        });
                    }
                    textField.setText(getItem());
                    setGraphic(textField);
                    setText(null);
                    javafx.application.Platform.runLater(() -> {
                        textField.requestFocus();
                        textField.selectAll();
                    });
                }
            }
            @Override public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else if (isEditing()) {
                    if (textField != null) textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        colProduct.setOnEditCommit(e -> {
            GdTransfer row = e.getRowValue();
            row.setProductName(e.getNewValue());
            
            // Auto-fill From Location based on stock
            if (row.getPrevLocation() == null || row.getPrevLocation().isBlank()) {
                cache.getStockRows().stream()
                     .filter(s -> s.getProductName().equalsIgnoreCase(e.getNewValue()) && s.getCartons() > 0)
                     .findFirst()
                     .ifPresent(stock -> row.setPrevLocation(stock.getLocation()));
            }
            
            persistRow(row, false);
            if (Boolean.TRUE.equals(table.getProperties().get("enterPressed"))) {
                table.getProperties().put("enterPressed", false);
                javafx.application.Platform.runLater(() -> 
                    table.edit(e.getTablePosition().getRow(), table.getColumns().get(2))
                );
            }
        });

        TableColumn<GdTransfer, String> colFrom = new TableColumn<>("From (Prev)");
        colFrom.setCellValueFactory(c -> c.getValue().prevLocationProperty());
        colFrom.setEditable(true);
        colFrom.setCellFactory(c -> new TableCell<>() {
            private com.sfw.wholesale.ui.component.AutoSuggestTextField textField;
            
            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (textField == null) {
                        java.util.List<String> suggestions = java.util.Arrays.asList(WAREHOUSES);
                        textField = new com.sfw.wholesale.ui.component.AutoSuggestTextField(javafx.collections.FXCollections.observableArrayList(suggestions));
                        textField.getStyleClass().add("styled-text-field");
                        
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                        textField.focusedProperty().addListener((obs, was, is) -> {
                            if (!is && isEditing()) commitEdit(textField.getText());
                        });
                    }
                    textField.setText(getItem());
                    setGraphic(textField);
                    setText(null);
                    javafx.application.Platform.runLater(() -> {
                        textField.requestFocus();
                        textField.selectAll();
                    });
                }
            }
            @Override public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else if (isEditing()) {
                    if (textField != null) textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        colFrom.setOnEditCommit(e -> {
            e.getRowValue().setPrevLocation(e.getNewValue());
            persistRow(e.getRowValue(), false);
            if (Boolean.TRUE.equals(table.getProperties().get("enterPressed"))) {
                table.getProperties().put("enterPressed", false);
                javafx.application.Platform.runLater(() -> 
                    table.edit(e.getTablePosition().getRow(), table.getColumns().get(3))
                );
            }
        });

        TableColumn<GdTransfer, String> colTo = new TableColumn<>("To (Updated)");
        colTo.setCellValueFactory(c -> c.getValue().updatedLocationProperty());
        colTo.setEditable(true);
        colTo.setCellFactory(c -> new TableCell<>() {
            private com.sfw.wholesale.ui.component.AutoSuggestTextField textField;
            
            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (textField == null) {
                        java.util.List<String> suggestions = java.util.Arrays.asList(DESTINATIONS);
                        textField = new com.sfw.wholesale.ui.component.AutoSuggestTextField(javafx.collections.FXCollections.observableArrayList(suggestions));
                        textField.getStyleClass().add("styled-text-field");
                        
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                        textField.focusedProperty().addListener((obs, was, is) -> {
                            if (!is && isEditing()) commitEdit(textField.getText());
                        });
                    }
                    textField.setText(getItem());
                    setGraphic(textField);
                    setText(null);
                    javafx.application.Platform.runLater(() -> {
                        textField.requestFocus();
                        textField.selectAll();
                    });
                }
            }
            @Override public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else if (isEditing()) {
                    if (textField != null) textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        colTo.setOnEditCommit(e -> {
            e.getRowValue().setUpdatedLocation(e.getNewValue());
            persistRow(e.getRowValue(), false);
            if (Boolean.TRUE.equals(table.getProperties().get("enterPressed"))) {
                table.getProperties().put("enterPressed", false);
                javafx.application.Platform.runLater(() -> 
                    table.edit(e.getTablePosition().getRow(), table.getColumns().get(4))
                );
            }
        });

        TableColumn<GdTransfer, String> colQty = new TableColumn<>("Qty (Ctns)");
        colQty.setMaxWidth(90);
        colQty.setEditable(true);
        colQty.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().getQtyCartons())));
        // Custom cell so we can requestFocus on the TextField (TextFieldTableCell loses focus on programmatic edit)
        colQty.setCellFactory(col -> new TableCell<>() {
            private javafx.scene.control.TextField tf;

            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (tf == null) {
                        tf = new javafx.scene.control.TextField();
                        tf.getStyleClass().add("styled-text-field");
                        tf.setOnAction(e -> commitEdit(tf.getText()));
                        tf.focusedProperty().addListener((obs, was, is) -> {
                            if (!is && isEditing()) commitEdit(tf.getText());
                        });
                        // Select-all on Tab so the old number is cleared
                        tf.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                            if (ev.getCode() == KeyCode.TAB) { commitEdit(tf.getText()); ev.consume(); }
                        });
                    }
                    tf.setText(getItem());
                    setGraphic(tf);
                    setText(null);
                    javafx.application.Platform.runLater(() -> {
                        tf.requestFocus();
                        tf.selectAll();           // pre-select so typing replaces old value
                    });
                }
            }
            @Override public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else if (isEditing()) {
                    if (tf != null) tf.setText(item);
                    setGraphic(tf);
                    setText(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        colQty.setOnEditCommit(e -> {
            try {
                e.getRowValue().setQtyCartons(Integer.parseInt(e.getNewValue()));
            } catch (NumberFormatException ignored) {
            }
            persistRow(e.getRowValue(), false);
            if (Boolean.TRUE.equals(table.getProperties().get("enterPressed"))) {
                table.getProperties().put("enterPressed", false);
                int rowIdx = e.getTablePosition().getRow();
                // First Enter on Qty: save and stay on this cell.
                // The NEXT Enter (global handler) will create a new row.
                javafx.application.Platform.runLater(() -> {
                    table.getSelectionModel().select(rowIdx, colQty);
                    table.getFocusModel().focus(rowIdx, colQty);
                    table.scrollTo(rowIdx);
                    table.requestFocus();
                    // Set flag AFTER restoring selection, bypassing any selection change listeners
                    lastEnterRowIdx = rowIdx;
                });
            }
        });

        // Done checkbox column — special behaviour
        TableColumn<GdTransfer, Boolean> colDone = new TableColumn<>("Done");
        colDone.setMaxWidth(70);
        colDone.setEditable(false); // handled manually via row click / Spacebar
        colDone.setCellValueFactory(c -> c.getValue().doneProperty().asObject());
        colDone.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.getStyleClass().add("done-checkbox");
                cb.setOnAction(event -> {
                    GdTransfer row = getTableRow().getItem();
                    if (row == null)
                        return;
                    handleDoneToggle(row, cb.isSelected());
                    event.consume();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                cb.setSelected(item);
                setGraphic(cb);
                setText(null);
            }
        });

        table.getColumns().setAll(Arrays.asList(colDate, colProduct, colFrom, colTo,
            colQty, colDone));

        // ── Keyboard navigation ────────────────────────────────────────────────
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (table.getEditingCell() != null) {
                    table.getProperties().put("enterPressed", true);
                    return;
                }
                
                TablePosition<?,?> focused = table.getFocusModel().getFocusedCell();
                // Two-step Enter: second Enter after staying on Qty cell → new row at Product
                if (focused != null && focused.getTableColumn() == colQty && focused.getRow() == lastEnterRowIdx) {
                    lastEnterRowIdx = -1;
                    addNewTransferRow();
                    event.consume();
                    return;
                }
                
                lastEnterRowIdx = -1; // Clear state on any other normal Enter navigation
                // Default: save current row and advance
                GdTransfer selected = table.getSelectionModel().getSelectedItem();
                if (selected != null)
                    persistRow(selected, true);
                int idx = table.getSelectionModel().getSelectedIndex();
                if (idx + 1 < table.getItems().size()) {
                    table.getSelectionModel().select(idx + 1);
                    table.scrollTo(idx + 1);   // keep cursor in view
                } else {
                    // At last row — add a new one
                    addNewTransferRow();
                }
                event.consume();
            }
        });

        // ── Colour rows by Done status ─────────────────────────────────────────
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(GdTransfer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    item.doneProperty().addListener((obs, o, done) -> applyRowStyle(this, done));
                    applyRowStyle(this, item.isDone());
                }
            }
        });

        return table;
    }

    // ── Done toggle logic ─────────────────────────────────────────────────────

    private void handleDoneToggle(GdTransfer row, boolean newDone) {
        try {
            if (newDone && !row.isDone()) {
                // Mark Done → apply stock movement
                svc.markDone(row);
            } else if (!newDone && row.isDone()) {
                // Unmark Done → reverse stock movement (with confirmation)
                boolean confirmed = ConfirmDialog.show(
                        "Reverse Transfer",
                        "This will reverse the stock movement for:\n" +
                                row.getProductName() +
                                "  " + row.getPrevLocation() + " → " + row.getUpdatedLocation() +
                                "\n" + row.getQtyCartons() + " cartons\n\n" +
                                "Are you sure?");
                if (confirmed) {
                    svc.undoDone(row);
                } else {
                    // Revert checkbox state visually
                    table.refresh();
                }
            }
        } catch (Exception ex) {
            ConfirmDialog.showError("Transfer Error", ex.getMessage());
            table.refresh();
        }
    }

    private void saveAllRemainingTransfers() {
        long count = displayList.stream().filter(t -> !t.isDone() && t.getProductName() != null && !t.getProductName().isBlank()).count();
        if (count == 0) return;

        boolean confirmed = ConfirmDialog.show("Save All Pending", "Are you sure you want to mark " + count + " pending transfers as Done?");
        if (!confirmed) return;

        int errorCount = 0;
        java.util.List<GdTransfer> pending = new java.util.ArrayList<>(displayList);
        for (GdTransfer t : pending) {
            if (!t.isDone() && t.getProductName() != null && !t.getProductName().isBlank()) {
                try {
                    svc.markDone(t);
                } catch (Exception e) {
                    errorCount++;
                    LOG.warning("Failed to mark done for " + t.getProductName() + ": " + e.getMessage());
                }
            }
        }
        
        table.refresh();
        if (errorCount > 0) {
            ConfirmDialog.showError("Batch Save Complete", "Saved all pending transfers, but " + errorCount + " transfers failed. Check logs for details.");
        } else {
            com.sfw.wholesale.ui.component.ConfirmDialog.showInfo("Batch Save Complete", "Successfully saved all pending transfers.");
        }
    }

    // ── Data operations ────────────────────────────────────────────────────────

    private void addNewTransferRow() {
        GdTransfer t = new GdTransfer();
        t.setTransferDate(LocalDate.now());
        t.setProductName("");
        t.setPrevLocation("");      // intentionally blank — user must choose
        t.setUpdatedLocation("");   // intentionally blank — user must choose
        t.setQtyCartons(1);
        t.setPairs(0);
        t.setDone(false);

        // Add to display only — do NOT persist to DB yet.
        displayList.add(t);
        cache.getTransferList().add(t);

        int newIdx = displayList.size() - 1;
        table.scrollTo(t);
        javafx.application.Platform.runLater(() -> {
            TableColumn<GdTransfer, ?> productCol = table.getColumns().get(1);
            table.scrollToColumn(productCol);
            table.getSelectionModel().select(newIdx, productCol);
            table.edit(newIdx, productCol);
        });
    }

    private void persistRow(GdTransfer row, boolean explicitSave) {
        // Compute pairs accurately from FIFO stock (pairsPerCarton of oldest batch)
        if (row.getQtyCartons() > 0 && !row.getProductName().isBlank()
                && row.getPrevLocation() != null && !row.getPrevLocation().isBlank()) {
            int ppc = stockSvc.getPairsPerCartonForFifo(
                    row.getProductName(), row.getPrevLocation());
            row.setPairs(row.getQtyCartons() * ppc);
        }
        try {
            svc.saveTransfer(row);
            table.refresh();
        } catch (IllegalArgumentException ex) {
            // Row is still incomplete (e.g. blank product or same src=dst) — silently skip,
            // the user has not finished filling it in yet.
            if (explicitSave) {
                ConfirmDialog.showError("Incomplete Row", ex.getMessage());
            } else {
                LOG.fine("Row not ready to persist: " + ex.getMessage());
            }
        } catch (Exception ex) {
            ConfirmDialog.showError("Save Error", ex.getMessage());
        }
    }

    private void deleteSelected() {
        GdTransfer selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;
        String msg = selected.isDone()
                ? "Delete this completed transfer? This will REVERSE the stock movement.\nAre you sure?"
                : "Delete this pending transfer?";
        if (ConfirmDialog.show("Delete Transfer", msg)) {
            try {
                svc.deleteTransfer(selected);
            } catch (Exception ex) {
                ConfirmDialog.showError("Delete Error", ex.getMessage());
            }
        }
    }

    private void refreshDisplay() {
        // Main tab always shows TODAY's transfers only.
        // All historical records are accessible via the "Previous Transfers" window.
        final LocalDate today = LocalDate.now();
        String search = searchField != null ? searchField.getText().toLowerCase() : "";

        displayList.setAll(cache.getTransferList().stream()
                .filter(t -> {
                    LocalDate d = t.getTransferDate();
                    // Include unsaved (in-memory) rows that have today's date or no date yet
                    if (d != null && !d.equals(today))
                        return false;
                    if (!search.isBlank() && !t.getProductName().toLowerCase().contains(search))
                        return false;
                    return true;
                })
                .sorted((a, b) -> {
                    int idA = a.getId() == 0 ? Integer.MAX_VALUE : a.getId();
                    int idB = b.getId() == 0 ? Integer.MAX_VALUE : b.getId();
                    return Integer.compare(idA, idB);
                })
                .collect(Collectors.toList()));
        javafx.application.Platform.runLater(() -> {
            if (!displayList.isEmpty()) {
                table.scrollTo(displayList.size() - 1);
            }
        });
    }

    // ── Row styling ────────────────────────────────────────────────────────────

    private void applyRowStyle(TableRow<GdTransfer> row, boolean done) {
        if (done) {
            row.setStyle("-fx-background-color: #dcfce7;");
        } else {
            row.setStyle("");
        }
    }

    // ── Column factories ──────────────────────────────────────────────────────

    private TableColumn<GdTransfer, String> textEditCol(String title) {
        TableColumn<GdTransfer, String> col = new TableColumn<>(title);
        col.setEditable(true);
        col.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
        return col;
    }



}
