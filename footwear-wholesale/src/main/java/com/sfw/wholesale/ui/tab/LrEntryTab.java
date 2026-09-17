package com.sfw.wholesale.ui.tab;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.model.*;
import com.sfw.wholesale.service.*;
import com.sfw.wholesale.ui.component.*;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.application.Platform;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.util.Callback;

import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

/**
 * Tab 1: LR Entry
 *
 * Layout: SplitPane (resizable)
 *   Left  (~45%) — header form + item lines table + action buttons
 *   Right (~55%) — searchable/filterable LR list; double-click to load
 *
 * All interaction is keyboard-first.  Double-click a cell in the item table to edit.
 * Item totals auto-update live.  Location field auto-fills on product suggestion selection.
 */
public class LrEntryTab extends VBox implements TabShortcutHandler {

    private static final Logger LOG = Logger.getLogger(LrEntryTab.class.getName());
    private static final String[] LR_ITEM_LOCATIONS = {"G1","G2","G3","G4","G5","RK2","Shop"};

    private final DataCache  cache  = DataCache.getInstance();
    private final LrService  lrSvc  = new LrService();

    // ── Current edit state ────────────────────────────────────────────────────
    private LrEntry currentEntry = new LrEntry();
    private final ObservableList<LrItem> currentItems = FXCollections.observableArrayList();
    // ── Left pane — header form fields ────────────────────────────────────────
    private TextField         lrNumberField;
    private DatePicker        lrDatePicker;
    private ComboBox<String>  transportCombo;


    // ── Left pane — item lines table ─────────────────────────────────────────
    private TableView<LrItem>          itemTable;
    private TableColumn<LrItem,Number> colLineNo, colCartons, colPpc;
    private TableColumn<LrItem,String> colProduct, colLocation;
    private TableColumn<LrItem,Number> colShop;
    private ScrollPane                    leftScrollPane;
    private Label                         totalLabel;

    @Override
    public void handleShortcut(javafx.scene.input.KeyEvent event) {
        if (event.isControlDown()) {
            if (event.getCode() == javafx.scene.input.KeyCode.S) {
                saveLr();
                event.consume();
            } else if (event.getCode() == javafx.scene.input.KeyCode.N) {
                clearForm();
                event.consume();
            }
        }
    }

    public LrEntryTab() {
        getChildren().add(buildLeftPane());
        VBox.setVgrow(getChildren().get(0), Priority.ALWAYS);
        getStyleClass().add("pane-bg");
        
        Platform.runLater(() -> {
            if (lrNumberField != null) {
                lrNumberField.requestFocus();
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEFT PANE
    // ════════════════════════════════════════════════════════════════════════

    private ScrollPane buildLeftPane() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("pane-bg");
        root.setMaxWidth(1100);
        root.setAlignment(Pos.TOP_CENTER);
        
        VBox centerWrapper = new VBox(root);
        centerWrapper.setAlignment(Pos.TOP_CENTER);

        // Build header form first — creates all field instance vars as side-effects
        Label    formTitle  = buildFormTitle();
        GridPane headerForm = buildHeaderForm();

        // Form field zoom slider (must be created AFTER buildHeaderForm() populates the field refs)
        java.util.prefs.Preferences formPrefs = java.util.prefs.Preferences.userNodeForPackage(LrEntryTab.class);
        double savedFormZoom = formPrefs.getDouble("formZoomFontSize", 14.0);

        Slider formSlider = new Slider(12, 24, savedFormZoom);
        formSlider.setShowTickMarks(true);
        formSlider.setMajorTickUnit(4);
        formSlider.setPrefWidth(130);
        formSlider.valueProperty().addListener((obs, old, val) -> {
            applyFormZoom(val.doubleValue());
            formPrefs.putDouble("formZoomFontSize", val.doubleValue());
        });

        Label formZoomLbl = new Label("Field Size:");
        formZoomLbl.getStyleClass().add("form-label");
        HBox formZoomBar = new HBox(8, formZoomLbl, formSlider);
        formZoomBar.setAlignment(Pos.CENTER_RIGHT);

        applyFormZoom(savedFormZoom); // apply saved zoom immediately to newly-created fields

        root.getChildren().addAll(
                formTitle,
                formZoomBar,
                headerForm,
                buildItemSection(),
                buildActionButtons()
        );

        leftScrollPane = new ScrollPane(centerWrapper);
        leftScrollPane.setFitToWidth(true);
        leftScrollPane.setFitToHeight(false);
        leftScrollPane.getStyleClass().add("scroll-pane");
        return leftScrollPane;
    }

    private Label buildFormTitle() {
        Label title = new Label("LR Entry");
        title.getStyleClass().add("tab-section-title");
        return title;
    }

    private GridPane buildHeaderForm() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.getStyleClass().add("form-grid");

        // Column constraints — two columns, both grow
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        // Row 0: LR Number | Received Date
        lrNumberField = styledTextField("LR Number *");
        lrNumberField.setId("lrNumberField");
        lrDatePicker  = styledDatePicker();
        lrDatePicker.setId("lrDatePicker");
        grid.add(labeledField("LR Number *", lrNumberField), 0, 0);
        grid.add(labeledField("Received Date *",   lrDatePicker),  1, 0);

        // Row 1: Transport Company
        transportCombo = new ComboBox<>(cache.getTransportNames());
        transportCombo.setId("transportComboBox");
        transportCombo.setEditable(true);
        transportCombo.setMaxWidth(Double.MAX_VALUE);
        transportCombo.getStyleClass().add("styled-combo");
        transportCombo.setPromptText("Select or type new...");

        grid.add(labeledField("Transport Company *", transportCombo), 0, 1);

        // Auto-scroll to top when fields receive focus
        javafx.beans.value.ChangeListener<Boolean> focusScrollListener = (obs, old, isFocused) -> {
            if (isFocused && leftScrollPane != null) {
                leftScrollPane.setVvalue(0.0);
            }
        };
        lrNumberField.focusedProperty().addListener(focusScrollListener);
        lrDatePicker.focusedProperty().addListener(focusScrollListener);
        transportCombo.focusedProperty().addListener(focusScrollListener);

        // Enter key navigation sequence
        setEnterJump(lrNumberField, lrDatePicker);
        setEnterJump(lrDatePicker, transportCombo);

        // Jump to item table when Enter is pressed on transport company
        transportCombo.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                javafx.application.Platform.runLater(() -> {
                    if (currentItems.isEmpty()) {
                        addItemRow();
                    } else {
                        itemTable.scrollToColumn(colProduct);
                        itemTable.getSelectionModel().select(0, colProduct);
                        itemTable.edit(0, colProduct);
                    }
                });
            }
        });

        return grid;
    }

    private void setEnterJump(javafx.scene.Node current, javafx.scene.Node next) {
        current.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                javafx.application.Platform.runLater(() -> {
                    next.requestFocus();
                    if (next instanceof TextInputControl) {
                        ((TextInputControl) next).selectAll();
                    } else if (next instanceof ComboBox && ((ComboBox<?>) next).isEditable()) {
                        ((ComboBox<?>) next).getEditor().selectAll();
                    } else if (next instanceof DatePicker) {
                        ((DatePicker) next).getEditor().selectAll();
                    }
                });
            }
        });
    }

    private VBox buildItemSection() {
        VBox box = new VBox(10);

        Label title = new Label("Item Lines");
        title.getStyleClass().add("section-subtitle");

        itemTable = buildItemTable();

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(LrEntryTab.class);
        double savedFontSize = prefs.getDouble("tableZoomFontSize", 14.0);

        Slider rowHeightSlider = new Slider(12, 36, savedFontSize);
        rowHeightSlider.setShowTickMarks(true);
        rowHeightSlider.setMajorTickUnit(4);
        rowHeightSlider.setPrefWidth(120);
        
        rowHeightSlider.valueProperty().addListener((obs, old, val) -> {
            double fontSize = val.doubleValue();
            itemTable.setStyle("-fx-font-size: " + fontSize + "px;");
            itemTable.setFixedCellSize((fontSize * 1.5) + 16); // padding for row height
            prefs.putDouble("tableZoomFontSize", fontSize);
        });
        
        itemTable.setStyle("-fx-font-size: " + savedFontSize + "px;");
        itemTable.setFixedCellSize((savedFontSize * 1.5) + 16);

        Label zoomLabel = new Label("Zoom: ");
        zoomLabel.getStyleClass().add("form-label");
        HBox zoomBox = new HBox(5, zoomLabel, rowHeightSlider);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox tableButtons = new HBox(10);
        tableButtons.setAlignment(Pos.CENTER_LEFT);
        tableButtons.getChildren().addAll(
                actionButton("+ Add Row", "btn-primary", e -> addItemRow()),
                actionButton("✕ Delete Row","btn-danger",  e -> deleteSelectedRow()),
                actionButton("⟳ Clear All","btn-secondary",e -> clearAllRows()),
                spacer,
                zoomBox
        );

        // Running totals bar
        totalLabel = new Label();
        totalLabel.setId("totalLabel");
        totalLabel.getStyleClass().add("total-bar-label");
        updateTotalBar();
        currentItems.addListener((ListChangeListener<LrItem>) c -> {
            while (c.next()) {
                if (c.wasAdded()) for (LrItem added : c.getAddedSubList()) addItemPropertyListeners(added);
                // No need to remove listeners explicitly—garbage collection handles detached items.
            }
            updateTotalBar();
        });

        box.getChildren().addAll(title, tableButtons, itemTable, totalLabel);
        VBox.setVgrow(itemTable, Priority.ALWAYS);
        return box;
    }

    private TableView<LrItem> buildItemTable() {
        TableView<LrItem> table = new TableView<>(currentItems);
        table.setId("lrItemTable");
        table.setEditable(true);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setMinHeight(220);
        table.getStyleClass().add("stock-table");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No items — click \"+ Add Row\" to begin."));

        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    e.consume();
                    moveToPreviousCell();
                    return;
                }

                table.getProperties().put("enterPressed", true);
                if (table.getEditingCell() == null) {
                    javafx.scene.control.TablePosition<?, ?> pos = table.getFocusModel().getFocusedCell();
                    if (pos != null && pos.getTableColumn() == colLocation) {
                        moveToNextCell();
                        e.consume();
                    }
                }
            } else if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                if (table.getEditingCell() == null) {
                    LrItem selected = table.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        currentItems.remove(selected);
                        e.consume();
                    }
                }
            }
        });
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                table.getProperties().put("enterPressed", false);
            }
        });

        colLineNo  = numCol("No.", 40, it -> it.getValue().lineNoProperty());
        
        colProduct = new TableColumn<>("Product Name");
        colProduct.setCellValueFactory(it -> it.getValue().productNameProperty());
        colProduct.setMinWidth(180);
        colProduct.setPrefWidth(220);
        colProduct.setMaxWidth(Double.MAX_VALUE);
        colProduct.setEditable(true);
        colProduct.setCellFactory(col -> new TableCell<>() {
            private AutoSuggestTextField textField;
            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (textField == null) {
                        textField = new AutoSuggestTextField(cache.getProductSuggestions());
                        textField.getStyleClass().add("styled-text-field");
                        
                        textField.setOnSuggestionSelected(selected -> {
                            int idx = selected.lastIndexOf(" (");
                            if (idx != -1 && selected.endsWith(")")) {
                                String prod = selected.substring(0, idx);
                                String loc = selected.substring(idx + 2, selected.length() - 1);
                                textField.setTextSilent(prod);
                                LrItem row = getTableRow().getItem();
                                if (row != null) {
                                    row.setLocation(loc);
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
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else if (isEditing()) {
                    if (textField != null) textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                    adjustProductColumnWidth(item);
                } else {
                    setText(item);
                    setGraphic(null);
                    adjustProductColumnWidth(item);
                }
            }
            private void adjustProductColumnWidth(String text) {
                if (text == null) return;
                javafx.scene.text.Text measurer = new javafx.scene.text.Text(text);
                measurer.setFont(getFont() != null ? getFont() : Font.getDefault());
                double width = measurer.getLayoutBounds().getWidth() + 80;
                if (width > colProduct.getPrefWidth()) {
                    colProduct.setPrefWidth(Math.min(width, 520));
                }
            }
        });
        colCartons = numEditCol("Ctns", 65, it -> it.getValue().cartonsProperty());
        colPpc     = numEditCol("Prs/Ctn", 75, it -> it.getValue().pairsPerCartonProperty());
        colLocation = locationSuggestCol("Location", 90);
        colShop    = numEditCol("Shop Ctns", 75, it -> it.getValue().shopCartonsProperty());
        
        table.getColumns().setAll(Arrays.asList(colLineNo, colProduct, colCartons,
            colPpc, colShop, colLocation));

        // Wire editable column commits to model setters
        colProduct.setOnEditCommit(e -> {
            e.getRowValue().setProductName(e.getNewValue());
            tryAutoFillLocationFromProduct(e.getRowValue(), e.getNewValue());
            moveToNextCell();
        });
        colCartons.setOnEditCommit(e -> {
            try { 
                e.getRowValue().setCartons(Integer.parseInt(e.getNewValue().toString())); 
                moveToNextCell();
            }
            catch (Exception ex) {
                ConfirmDialog.showError("Invalid Input", "Cartons must be a whole number.");
                table.refresh();
            }
        });
        colShop.setOnEditCommit(e -> {
            try { 
                int val = Integer.parseInt(e.getNewValue().toString());
                if (val > e.getRowValue().getCartons()) {
                    ConfirmDialog.showError("Invalid Input", "Shop cartons cannot exceed total cartons.");
                    table.refresh();
                } else {
                    e.getRowValue().setShopCartons(val); 
                    moveToNextCell();
                }
            }
            catch (Exception ex) {
                ConfirmDialog.showError("Invalid Input", "Shop cartons must be a whole number.");
                table.refresh();
            }
        });
        colPpc.setOnEditCommit(e -> {
            try { 
                e.getRowValue().setPairsPerCarton(Integer.parseInt(e.getNewValue().toString())); 
                moveToNextCell();
            }
            catch (Exception ex) {
                ConfirmDialog.showError("Invalid Input", "Pairs per Carton must be a whole number.");
                table.refresh();
            }
        });

        return table;
    }

    private HBox buildActionButtons() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 0, 0, 0));

        Button saveBtn = actionButton("💾 Save LR", "btn-primary", e -> saveLr());
        saveBtn.setId("saveLrButton");
        Button clearBtn = actionButton("⟳ New LR", "btn-secondary", e -> clearForm());
        clearBtn.setId("newLrButton");
        saveBtn.setPrefWidth(140);

        box.getChildren().addAll(saveBtn, clearBtn);
        return box;
    }

    // ════════════════════════════════════════════════════════════════════════
    public void loadLrForEditing(LrEntry entry) {
        currentEntry = entry;
        lrNumberField.setText(entry.getLrNumber());
        lrDatePicker.setValue(entry.getLrDate());
        transportCombo.setValue(entry.getTransportCompany());

        // Load item lines from DB
        currentItems.clear();
        try {
            currentItems.addAll(lrSvc.loadItemsForLr(entry.getId()));
        } catch (Exception ex) {
            ConfirmDialog.showError("Load Error", "Failed to load item lines: " + ex.getMessage());
        }
    }

    private void saveLr() {
        // Collect form values into currentEntry
        currentEntry.setLrNumber(lrNumberField.getText().trim());
        currentEntry.setLrDate(lrDatePicker.getValue());
        currentEntry.setTransportCompany(
                transportCombo.getValue() != null ? transportCombo.getValue().trim() : "");

        // Validate duplicate LR number
        try {
            if (lrSvc.lrNumberExists(currentEntry.getLrNumber(), currentEntry.getId())) {
                ConfirmDialog.showError("Duplicate LR",
                        "LR Number \"" + currentEntry.getLrNumber() + "\" already exists.");
                return;
            }
        } catch (Exception ex) {
            ConfirmDialog.showError("Validation Error", ex.getMessage());
            return;
        }

        try {
            lrSvc.saveLr(currentEntry, new ArrayList<>(currentItems));
            ConfirmDialog.showInfo("Saved", "LR saved successfully.");
            clearForm();
        } catch (IllegalArgumentException ex) {
            ConfirmDialog.showError("Validation Error", ex.getMessage());
        } catch (Exception ex) {
            ConfirmDialog.showError("Save Error", "Could not save LR: " + ex.getMessage());
            LOG.severe("LR save error: " + ex.getMessage());
        }
    }

    private void addItemRow() {
        LrItem item = new LrItem();
        item.setLineNo(currentItems.size() + 1);
        item.setLocation("G1");
        currentItems.add(item);
        int rowIndex = currentItems.size() - 1;
        itemTable.scrollTo(item);
        javafx.application.Platform.runLater(() -> {
            TableColumn<LrItem, ?> productCol = itemTable.getColumns().get(1);
            itemTable.scrollToColumn(productCol);
            itemTable.getSelectionModel().select(rowIndex, productCol);
            itemTable.edit(rowIndex, productCol);
        });
    }

    private void deleteSelectedRow() {
        LrItem selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (ConfirmDialog.show("Delete Row", "Remove this item line?")) {
            currentItems.remove(selected);
            renumberLines();
        }
    }

    private void clearAllRows() {
        if (currentItems.isEmpty()) return;
        if (ConfirmDialog.show("Clear All Rows", "Remove all item lines from this LR?")) {
            currentItems.clear();
        }
    }

    private void clearForm() {
        currentEntry = new LrEntry();
        currentItems.clear();
        lrNumberField.clear();
        lrDatePicker.setValue(LocalDate.now());
        transportCombo.setValue(null);
        javafx.application.Platform.runLater(() -> {
            if (lrNumberField != null) lrNumberField.requestFocus();
        });
    }

    private void renumberLines() {
        for (int i = 0; i < currentItems.size(); i++) {
            currentItems.get(i).setLineNo(i + 1);
        }
    }

    private void tryAutoFillLocationFromProduct(LrItem row, String rawText) {
        // rawText might be "SWEETY 5X8 (G4)" from the autosuggest
        if (rawText.contains("(") && rawText.endsWith(")")) {
            String loc = rawText.substring(rawText.lastIndexOf('(') + 1, rawText.length() - 1).trim();
            if (isWarehouse(loc)) {
                row.setLocation(loc);
                // Strip the " (G4)" suffix from the product name
                String product = rawText.substring(0, rawText.lastIndexOf('(')).trim();
                row.setProductName(product);
                itemTable.refresh();
            }
        }
    }

    private void updateTotalBar() {
        int totalCtns  = currentItems.stream().mapToInt(LrItem::getCartons).sum();
        int totalPairs = currentItems.stream().mapToInt(LrItem::getTotalPairs).sum();
        totalLabel.setText(String.format("  Total: %d cartons  |  %d pairs", totalCtns, totalPairs));
        totalLabel.getStyleClass().add("total-bar-label");
    }

    private void addItemPropertyListeners(LrItem item) {
        item.cartonsProperty().addListener((obs, oldV, newV) -> updateTotalBar());
        item.shopCartonsProperty().addListener((obs, oldV, newV) -> updateTotalBar());
        item.pairsPerCartonProperty().addListener((obs, oldV, newV) -> updateTotalBar());
    }



    // ════════════════════════════════════════════════════════════════════════
    // TABLE COLUMN FACTORIES
    // ════════════════════════════════════════════════════════════════════════

    private TableColumn<LrItem,Number> numCol(String title, double width,
            Callback<TableColumn.CellDataFeatures<LrItem,Number>, javafx.beans.value.ObservableValue<Number>> factory) {
        TableColumn<LrItem,Number> col = new TableColumn<>(title);
        col.setCellValueFactory(factory);
        col.setMaxWidth(width);
        col.setMinWidth(width);
        col.setResizable(false);
        col.setEditable(false);
        return col;
    }

    private TableColumn<LrItem,Number> numEditCol(String title, double w, Callback<TableColumn.CellDataFeatures<LrItem,Number>, javafx.beans.value.ObservableValue<Number>> valFactory) {
        TableColumn<LrItem,Number> col = new TableColumn<>(title);
        col.setCellValueFactory(valFactory);
        col.setCellFactory(colArg -> new TextFieldTableCell<>(new javafx.util.converter.NumberStringConverter() {
            @Override
            public Number fromString(String value) {
                try {
                    return super.fromString(value);
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> 
                        ConfirmDialog.showError("Invalid Input", "Must be a valid number.")
                    );
                    throw e;
                }
            }
        }));
        col.setMinWidth(w);
        col.setPrefWidth(w);
        col.setEditable(true);
        return col;
    }

    private TableColumn<LrItem,String> locationSuggestCol(String title, double w) {
        TableColumn<LrItem,String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> c.getValue().locationProperty());
        col.setMaxWidth(w + 20);
        col.setEditable(true);
        col.setCellFactory(c -> new TableCell<>() {
            private AutoSuggestTextField textField;
            
            @Override public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    if (textField == null) {
                        List<String> suggestions = Arrays.asList(LR_ITEM_LOCATIONS);
                        textField = new AutoSuggestTextField(FXCollections.observableArrayList(suggestions));
                        textField.getStyleClass().add("styled-text-field");
                        
                        textField.setOnAction(e -> commitIfValid());
                        textField.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                                ev.consume();
                                String text = textField.getText().trim().toUpperCase();
                                if (isWarehouse(text)) {
                                    // BUG-14: normalise to canonical case before commit
                                    commitEdit(normalizeLocation(text));
                                    javafx.application.Platform.runLater(() -> {
                                        itemTable.getSelectionModel().select(getIndex(), colLocation);
                                        itemTable.getFocusModel().focus(getIndex(), colLocation);
                                        itemTable.requestFocus();
                                    });
                                } else {
                                    cancelEdit();
                                }
                            }
                        });
                        textField.focusedProperty().addListener((obs, was, is) -> {
                            if (!is && isEditing()) commitIfValid();
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
            
            private void commitIfValid() {
                String text = textField.getText().trim().toUpperCase();
                if (isWarehouse(text)) {
                    // BUG-14: normalise to canonical case before commit
                    commitEdit(normalizeLocation(text));
                } else {
                    cancelEdit();
                }
            }
            
            @Override public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }
            
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else if (isEditing()) {
                    if (textField != null) textField.setText(item);
                    setGraphic(textField);
                    setText(null);
                } else {
                    setText(item);
                    setGraphic(null);
                }
            }
        });
        
        col.setOnEditCommit(e -> {
            e.getRowValue().setLocation(e.getNewValue());
        });
        
        return col;
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private VBox labeledField(String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        VBox box = new VBox(4, lbl, field);
        return box;
    }

    private TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("styled-text-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private DatePicker styledDatePicker() {
        DatePicker dp = new DatePicker(LocalDate.now());
        dp.setMaxWidth(Double.MAX_VALUE);
        dp.getStyleClass().add("styled-date-picker");
        return dp;
    }

    private Button actionButton(String text, String styleClass,
            javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.getStyleClass().add(styleClass);
        btn.setOnAction(handler);
        btn.getStyleClass().add("action-btn");
        return btn;
    }

    private boolean isWarehouse(String loc) {
        return loc != null && (loc.equals("G1") || loc.equals("G2") || loc.equals("G3")
                || loc.equals("G4") || loc.equals("G5") || loc.equals("RK2") || loc.equals("SHOP"));
    }

    /** Maps the upper-cased location token to its canonical stored form. */
    private String normalizeLocation(String upperLoc) {
        if ("SHOP".equals(upperLoc)) return "Shop";
        return upperLoc; // G1-G5, RK2 are already canonical
    }

    private void moveToNextCell() {
        javafx.application.Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            TablePosition<LrItem, ?> pos = (TablePosition<LrItem, ?>) itemTable.getFocusModel().getFocusedCell();
            if (pos == null) return;
            int nextColIndex = pos.getColumn() + 1;
            int rowIndex = pos.getRow();

            while (nextColIndex < itemTable.getColumns().size()) {
                TableColumn<LrItem, ?> nextCol = itemTable.getColumns().get(nextColIndex);
                if (nextCol.isEditable()) {
                    selectAndEditCell(rowIndex, nextCol);
                    return;
                }
                nextColIndex++;
            }

            if (rowIndex + 1 < itemTable.getItems().size()) {
                TableColumn<LrItem, ?> productCol = itemTable.getColumns().get(1);
                selectAndEditCell(rowIndex + 1, productCol);
            } else {
                addItemRow();
            }
        });
    }

    private void moveToPreviousCell() {
        javafx.application.Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            TablePosition<LrItem, ?> pos = (TablePosition<LrItem, ?>) itemTable.getFocusModel().getFocusedCell();
            if (pos == null) return;
            int prevColIndex = pos.getColumn() - 1;
            int rowIndex = pos.getRow();

            while (prevColIndex >= 0) {
                TableColumn<LrItem, ?> prevCol = itemTable.getColumns().get(prevColIndex);
                if (prevCol.isEditable()) {
                    selectAndEditCell(rowIndex, prevCol);
                    return;
                }
                prevColIndex--;
            }

            if (rowIndex > 0) {
                TableColumn<LrItem, ?> lastEditable = null;
                for (int i = itemTable.getColumns().size() - 1; i >= 0; i--) {
                    TableColumn<LrItem, ?> col = itemTable.getColumns().get(i);
                    if (col.isEditable()) {
                        lastEditable = col;
                        break;
                    }
                }
                if (lastEditable != null) {
                    selectAndEditCell(rowIndex - 1, lastEditable);
                }
            }
        });
    }

    private void selectAndEditCell(int rowIndex, TableColumn<LrItem, ?> column) {
        if (rowIndex < 0 || rowIndex >= itemTable.getItems().size()) return;
        itemTable.getSelectionModel().select(rowIndex, column);
        itemTable.getFocusModel().focus(rowIndex, column);
        itemTable.scrollTo(rowIndex);
        itemTable.scrollToColumn(column);
        if (leftScrollPane != null) {
            double rowHeight = itemTable.getFixedCellSize() > 0 ? itemTable.getFixedCellSize() : 24;
            double targetY = rowIndex * rowHeight;
            leftScrollPane.setVvalue(Math.min(1.0, Math.max(0.0, targetY / (itemTable.getItems().size() * rowHeight))));
        }
        javafx.application.Platform.runLater(() -> itemTable.edit(rowIndex, column));
    }

    /**
     * Applies font-size and preferred height to all header form controls.
     * Called from the form zoom slider and on startup to restore saved preference.
     */
    private void applyFormZoom(double fontSize) {
        String style = "-fx-font-size: " + fontSize + "px;";
        double h = Math.max(26, fontSize * 2.4);
        if (lrNumberField   != null) { lrNumberField.setStyle(style);   lrNumberField.setPrefHeight(h); }
        if (lrDatePicker    != null) { lrDatePicker.setStyle(style);    lrDatePicker.setPrefHeight(h); }
        if (transportCombo  != null) { transportCombo.setStyle(style);  transportCombo.setPrefHeight(h); }
    }
}
