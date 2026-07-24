package com.sfw.wholesale.ui.tab;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.model.LrEntry;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Tab 2: All LR Entries
 * 
 * Displays a searchable, filterable list of all LRs.
 * Double-clicking an LR switches to the Entry tab and loads it for editing.
 */
public class LrListTab extends VBox {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final DataCache cache = DataCache.getInstance();
    private final Consumer<LrEntry> onEditRequested;

    private TextField            lrSearchField;
    private DatePicker           fromDateFilter, toDateFilter;
    private TableView<LrEntry>   lrListTable;
    private final ObservableList<LrEntry> filteredLrList = FXCollections.observableArrayList();

    public LrListTab(Consumer<LrEntry> onEditRequested) {
        this.onEditRequested = onEditRequested;

        setSpacing(12);
        setPadding(new Insets(20));
        getStyleClass().add("pane-bg");

        Label title = new Label("All LR Entries");
        title.getStyleClass().add("tab-section-title");

        // Filter bar
        lrSearchField = styledTextField("Search LR / Transport...");
        lrSearchField.textProperty().addListener((obs, o, n) -> refreshLrList());



        // Date range
        fromDateFilter = styledDatePicker();
        fromDateFilter.setPromptText("From date");
        toDateFilter   = styledDatePicker();
        toDateFilter.setPromptText("To date");
        fromDateFilter.valueProperty().addListener((obs,o,n) -> refreshLrList());
        toDateFilter.valueProperty().addListener((obs,o,n) -> refreshLrList());

        HBox dateRange = new HBox(10,
                new Label("From:") {{ getStyleClass().add("form-label"); }},
                fromDateFilter,
                new Label("To:") {{ getStyleClass().add("form-label"); }},
                toDateFilter
        );
        dateRange.setAlignment(Pos.CENTER_LEFT);

        // LR list table
        lrListTable = buildLrListTable();

        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(LrEntryTab.class);
        double savedFontSize = prefs.getDouble("tableZoomFontSize", 14.0);

        Slider rowHeightSlider = new Slider(12, 36, savedFontSize);
        rowHeightSlider.setShowTickMarks(true);
        rowHeightSlider.setMajorTickUnit(4);
        rowHeightSlider.setPrefWidth(120);
        
        rowHeightSlider.valueProperty().addListener((obs, old, val) -> {
            double fontSize = val.doubleValue();
            lrListTable.setStyle("-fx-font-size: " + fontSize + "px;");
            lrListTable.setFixedCellSize((fontSize * 1.5) + 16);
            prefs.putDouble("tableZoomFontSize", fontSize);
        });
        
        lrListTable.setStyle("-fx-font-size: " + savedFontSize + "px;");
        lrListTable.setFixedCellSize((savedFontSize * 1.5) + 16);

        Label zoomLabel = new Label("Zoom: ");
        zoomLabel.getStyleClass().add("form-label");
        HBox zoomBox = new HBox(5, zoomLabel, rowHeightSlider);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterRow = new HBox(10, dateRange, spacer, zoomBox);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, lrSearchField, filterRow, lrListTable);
        VBox.setVgrow(lrListTable, Priority.ALWAYS);

        initListeners();
        refreshLrList();
    }

    private TableView<LrEntry> buildLrListTable() {
        TableView<LrEntry> table = new TableView<>(filteredLrList);
        table.setEditable(false);
        table.getStyleClass().add("stock-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No LR entries found."));

        TableColumn<LrEntry,String> colLrNo = new TableColumn<>("LR No.");
        colLrNo.setCellValueFactory(c -> c.getValue().lrNumberProperty());

        TableColumn<LrEntry,String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getLrDate();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "");
        });

        TableColumn<LrEntry,String> colTransport = new TableColumn<>("Transport");
        colTransport.setCellValueFactory(c -> c.getValue().transportCompanyProperty());

        TableColumn<LrEntry,Number> colTotCtns = new TableColumn<>("Ctns");
        colTotCtns.setCellValueFactory(c -> c.getValue().totalCartonsProperty());
        colTotCtns.setMaxWidth(60);

        table.getColumns().setAll(Arrays.asList(colLrNo, colDate, colTransport, colTotCtns));

        // Double-click → load LR into edit form via callback
        table.setRowFactory(tv -> {
            TableRow<LrEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    onEditRequested.accept(row.getItem());
                }
            });
            return row;
        });

        return table;
    }

    private void initListeners() {
        cache.getLrList().addListener((ListChangeListener<LrEntry>) c -> refreshLrList());
    }

    private void refreshLrList() {
        String search = lrSearchField != null ? lrSearchField.getText() : "";
        LocalDate from = fromDateFilter != null ? fromDateFilter.getValue() : null;
        LocalDate to   = toDateFilter   != null ? toDateFilter.getValue()   : null;

        List<LrEntry> filtered = cache.getLrList().stream()
                .filter(e -> matchesSearch(e, search))
                .filter(e -> matchesDateRange(e, from, to))
                .collect(Collectors.toList());

        filteredLrList.setAll(filtered);
    }

    private boolean matchesSearch(LrEntry e, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        return e.getLrNumber().toLowerCase().contains(q)
                || e.getTransportCompany().toLowerCase().contains(q);
    }



    private boolean matchesDateRange(LrEntry e, LocalDate from, LocalDate to) {
        LocalDate d = e.getLrDate();
        if (d == null) return true;
        if (from != null && d.isBefore(from)) return false;
        if (to   != null && d.isAfter(to))   return false;
        return true;
    }

    // Helpers for UI components
    private TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("styled-text-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private DatePicker styledDatePicker() {
        DatePicker dp = new DatePicker();
        dp.setMaxWidth(Double.MAX_VALUE);
        dp.getStyleClass().add("styled-date-picker");
        return dp;
    }

    private RadioButton filterRadio(String label, String data, ToggleGroup group) {
        RadioButton rb = new RadioButton(label);
        rb.setToggleGroup(group);
        rb.setUserData(data);
        rb.getStyleClass().add("radio-btn");
        return rb;
    }
}
