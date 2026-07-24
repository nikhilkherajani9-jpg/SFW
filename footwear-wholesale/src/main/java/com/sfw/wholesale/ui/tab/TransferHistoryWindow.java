package com.sfw.wholesale.ui.tab;

import com.sfw.wholesale.model.GdTransfer;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standalone non-modal window that shows the full GD Transfer history,
 * grouped by transfer date.
 *
 * Layout:
 *   Title + hint row
 *   Date filter bar  (DatePicker  |  Clear  |  Summary label)
 *   TreeTableView:
 *     Root (invisible)
 *      └─ Date group row  — Date | # Transfers (n done) | Total Cartons | — | — | —
 *           └─ Transfer row — — | — | Cartons | Product | From → To | Status
 *
 * Today's date group is auto-expanded on open.
 * Multiple instances can coexist (window is non-blocking).
 */
public class TransferHistoryWindow {

    private final ObservableList<GdTransfer> source;
    private TreeTableView<TRow> treeTable;

    public TransferHistoryWindow(ObservableList<GdTransfer> source) {
        this.source = source;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("GD Transfer History");
        stage.setMinWidth(920);
        stage.setMinHeight(560);

        treeTable = buildTreeTable();

        // ── Date filter ────────────────────────────────────────────────────────
        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Filter by date…");
        datePicker.getStyleClass().add("styled-date-picker");

        Button clearBtn = new Button("Clear Filter");
        clearBtn.getStyleClass().addAll("action-btn", "btn-secondary");

        Label summaryLabel = new Label();
        summaryLabel.getStyleClass().add("total-bar-label");

        datePicker.valueProperty().addListener((obs, old, val) -> {
            populateTree(val);
            updateSummary(summaryLabel, val);
        });
        clearBtn.setOnAction(e -> {
            datePicker.setValue(null);
            populateTree(null);
            updateSummary(summaryLabel, null);
        });

        Label dateLabel = new Label("Filter by Date:");
        dateLabel.getStyleClass().add("form-label");

        // ── Zoom slider ────────────────────────────────────────────────────────────
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(TransferHistoryWindow.class);
        double savedFontSize = prefs.getDouble("tableZoomFontSize", 14.0);

        Slider rowHeightSlider = new Slider(12, 36, savedFontSize);
        rowHeightSlider.setShowTickMarks(true);
        rowHeightSlider.setMajorTickUnit(4);
        rowHeightSlider.setPrefWidth(120);
        rowHeightSlider.valueProperty().addListener((obs, old, val) -> {
            double fontSize = val.doubleValue();
            treeTable.setStyle("-fx-font-size: " + fontSize + "px;");
            treeTable.setFixedCellSize((fontSize * 1.5) + 16);
            prefs.putDouble("tableZoomFontSize", fontSize);
        });
        treeTable.setStyle("-fx-font-size: " + savedFontSize + "px;");
        treeTable.setFixedCellSize((savedFontSize * 1.5) + 16);

        Label zoomLabel = new Label("Zoom: ");
        zoomLabel.getStyleClass().add("form-label");
        HBox zoomBox = new HBox(5, zoomLabel, rowHeightSlider);
        zoomBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterBar = new HBox(10, dateLabel, datePicker, clearBtn, spacer, zoomBox, new Separator(Orientation.VERTICAL), summaryLabel);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(0, 0, 8, 0));

        // ── Title & hint ───────────────────────────────────────────────────────
        Label title = new Label("\uD83D\uDCCB  Transfer History");
        title.getStyleClass().add("tab-section-title");

        Label hint = new Label(
                "Click \u25B6 next to a date to expand and see individual transfers.  " +
                "Today\u2019s transfers are expanded automatically.");
        hint.getStyleClass().add("hint-label");

        // ── Root layout ────────────────────────────────────────────────────────
        VBox root = new VBox(10, title, hint, filterBar, treeTable);
        root.setPadding(new Insets(20));
        VBox.setVgrow(treeTable, Priority.ALWAYS);
        root.getStyleClass().add("pane-bg");

        populateTree(null);
        updateSummary(summaryLabel, null);

        Scene scene = new Scene(root, 960, 640);
        try {
            scene.getStylesheets().add(
                    getClass().getResource("/css/app.css").toExternalForm());
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    // ── Tree construction ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TreeTableView<TRow> buildTreeTable() {
        TreeTableView<TRow> table = new TreeTableView<>();
        table.setShowRoot(false);
        table.getStyleClass().add("stock-table");
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No transfers found."));

        // Date — bold + blue for group rows
        TreeTableColumn<TRow, String> colDate = new TreeTableColumn<>("Date");
        colDate.setCellValueFactory(c -> c.getValue().getValue().dateString);
        colDate.setMinWidth(115);
        colDate.setCellFactory(col -> new TreeTableCell<>() {
            @SuppressWarnings("deprecation")
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setStyle(""); return; }
                setText(item);
                TreeItem<TRow> ti = getTreeTableRow().getTreeItem();
                boolean grp = ti != null && ti.getValue() != null && ti.getValue().isGroup;
                setStyle(grp ? "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e40af;" : "");
            }
        });

        // # Transfers (group row only)
        TreeTableColumn<TRow, String> colCount = new TreeTableColumn<>("Transfers");
        colCount.setCellValueFactory(c -> c.getValue().getValue().transferCount);
        colCount.setMinWidth(110);
        colCount.setMaxWidth(140);
        colCount.setStyle("-fx-alignment: CENTER;");

        // Cartons — group shows total; individual shows its own qty
        TreeTableColumn<TRow, String> colCartons = new TreeTableColumn<>("Cartons");
        colCartons.setCellValueFactory(c -> c.getValue().getValue().cartons);
        colCartons.setMaxWidth(90);
        colCartons.setCellFactory(col -> new TreeTableCell<>() {
            @SuppressWarnings("deprecation")
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                TreeItem<TRow> ti = getTreeTableRow().getTreeItem();
                boolean grp = ti != null && ti.getValue() != null && ti.getValue().isGroup;
                setStyle(grp
                        ? "-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;"
                        : "-fx-alignment: CENTER-RIGHT;");
            }
        });

        // Product (individual only)
        TreeTableColumn<TRow, String> colProduct = new TreeTableColumn<>("Product");
        colProduct.setCellValueFactory(c -> c.getValue().getValue().product);
        colProduct.setMinWidth(160);

        // From → To (individual only)
        TreeTableColumn<TRow, String> colRoute = new TreeTableColumn<>("From \u2192 To");
        colRoute.setCellValueFactory(c -> c.getValue().getValue().route);
        colRoute.setMinWidth(110);

        // Status — coloured text
        TreeTableColumn<TRow, String> colStatus = new TreeTableColumn<>("Status");
        colStatus.setCellValueFactory(c -> c.getValue().getValue().status);
        colStatus.setMaxWidth(100);
        colStatus.setCellFactory(col -> new TreeTableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.startsWith("\u2713"))      setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                else if (item.startsWith("\u25cf")) setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                else                               setStyle("");
            }
        });

        table.getColumns().setAll(Arrays.asList(
                colDate, colCount, colCartons, colProduct, colRoute, colStatus));
        return table;
    }

    private void populateTree(LocalDate dateFilter) {
        // Collect, filter, group by date
        Map<LocalDate, List<GdTransfer>> byDate = source.stream()
                .filter(t -> dateFilter == null || Objects.equals(t.getTransferDate(), dateFilter))
                .collect(Collectors.groupingBy(
                        t -> t.getTransferDate() != null ? t.getTransferDate() : LocalDate.MIN,
                        Collectors.toList()));

        // Dates sorted newest-first
        List<LocalDate> sortedDates = new ArrayList<>(byDate.keySet());
        sortedDates.sort(Comparator.reverseOrder());

        TreeItem<TRow> root = new TreeItem<>(new TRow()); // dummy invisible root
        LocalDate today = LocalDate.now();

        for (LocalDate date : sortedDates) {
            List<GdTransfer> transfers = byDate.get(date);
            int  totalCartons = transfers.stream().mapToInt(GdTransfer::getQtyCartons).sum();
            long doneCount    = transfers.stream().filter(GdTransfer::isDone).count();

            TreeItem<TRow> groupItem = new TreeItem<>(
                    new TRow(date, transfers.size(), totalCartons, (int) doneCount));

            // Sort individual rows by ID (insertion / chronological order)
            transfers.stream()
                     .sorted(Comparator.comparingInt(GdTransfer::getId))
                     .forEach(t -> groupItem.getChildren().add(new TreeItem<>(new TRow(t))));

            // Auto-expand today
            groupItem.setExpanded(date.equals(today));
            root.getChildren().add(groupItem);
        }

        treeTable.setRoot(root);
    }

    private void updateSummary(Label label, LocalDate dateFilter) {
        List<GdTransfer> relevant = source.stream()
                .filter(t -> dateFilter == null || Objects.equals(t.getTransferDate(), dateFilter))
                .collect(Collectors.toList());
        int total   = relevant.size();
        int cartons = relevant.stream().mapToInt(GdTransfer::getQtyCartons).sum();
        long done   = relevant.stream().filter(GdTransfer::isDone).count();
        label.setText(String.format(
                "Total: %d transfers  |  %d cartons  |  %d done  |  %d pending",
                total, cartons, done, total - done));
    }

    // ── Data model ─────────────────────────────────────────────────────────────

    /**
     * Unified row model for both date-group header rows and individual transfer rows.
     * Fields that are not relevant for a given row type are left as empty strings.
     */
    static class TRow {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        final StringProperty dateString    = new SimpleStringProperty("");
        final StringProperty transferCount = new SimpleStringProperty("");
        final StringProperty cartons       = new SimpleStringProperty("");
        final StringProperty product       = new SimpleStringProperty("");
        final StringProperty route         = new SimpleStringProperty("");
        final StringProperty status        = new SimpleStringProperty("");
        final boolean isGroup;

        /** Dummy root (never displayed) */
        TRow() { isGroup = false; }

        /** Date-group header row */
        TRow(LocalDate date, int count, int totalCartons, int doneCount) {
            isGroup = true;
            dateString.set(date != null && !date.equals(LocalDate.MIN)
                    ? date.format(FMT) : "Unknown Date");
            transferCount.set(count + " (" + doneCount + " done)");
            cartons.set(String.valueOf(totalCartons));
        }

        /** Individual transfer row */
        TRow(GdTransfer t) {
            isGroup = false;
            cartons.set(String.valueOf(t.getQtyCartons()));
            product.set(t.getProductName() != null ? t.getProductName() : "");
            String from = t.getPrevLocation()    != null ? t.getPrevLocation()    : "?";
            String to   = t.getUpdatedLocation() != null ? t.getUpdatedLocation() : "?";
            route.set(from + " \u2192 " + to);
            status.set(t.isDone() ? "\u2713 Done" : "\u25cf Pending");
        }
    }
}
