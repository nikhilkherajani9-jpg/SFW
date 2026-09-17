package com.sfw.wholesale.database;

import com.sfw.wholesale.model.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * In-memory cache loaded once at app startup.
 *
 * All tab UIs bind directly to these ObservableLists — no DB query happens when
 * a user navigates tabs, types in a search box, or opens a dropdown.
 * Database writes happen in the service layer; after each write the service calls
 * the appropriate refresh*() method to re-sync the relevant cache list.
 *
 * HDD performance rationale: loading 500–5000 rows once at startup is fast
 * (sequential read). All subsequent user interactions are pure in-memory ops.
 *
 * BUG-11 FIX: All ObservableList.setAll() mutations are now guarded by runOnFx()
 * so that calls from background threads (startup loader, future background tasks)
 * always route through Platform.runLater() while FXAT calls remain synchronous.
 */
public class DataCache {

    private static final Logger LOG = Logger.getLogger(DataCache.class.getName());
    private static DataCache instance;

    // ── Observable lists (tabs bind to these directly) ─────────────────────────
    private final ObservableList<LrEntry>    lrList       = FXCollections.observableArrayList();
    private final ObservableList<StockRow>   stockRows    = FXCollections.observableArrayList();
    private final ObservableList<GdTransfer> transferList = FXCollections.observableArrayList();

    // ── Autocomplete lists (loaded once, updated on new entries) ───────────────
    private final ObservableList<String> transportNames = FXCollections.observableArrayList();
    /** Format: "PRODUCT (LOCATION)" — only G1-G5/RK2, never Shop. */
    private final ObservableList<String> productSuggestions = FXCollections.observableArrayList();

    // ── Singleton ──────────────────────────────────────────────────────────────

    private DataCache() {}

    public static synchronized DataCache getInstance() {
        if (instance == null) instance = new DataCache();
        return instance;
    }

    // ── Public accessors ───────────────────────────────────────────────────────

    public ObservableList<LrEntry>    getLrList()            { return lrList; }
    public ObservableList<StockRow>   getStockRows()         { return stockRows; }
    public ObservableList<GdTransfer> getTransferList()      { return transferList; }
    public ObservableList<String>     getTransportNames()    { return transportNames; }
    public ObservableList<String>     getProductSuggestions(){ return productSuggestions; }

    // ── Full load (called once at startup) ─────────────────────────────────────

    public void loadAll(Connection conn) throws SQLException {
        LOG.info("Loading all caches from database...");
        loadTransports(conn);
        loadLrList(conn);
        loadStock(conn);
        loadTransfers(conn);
        // buildProductSuggestions is called inside loadStock already
        LOG.info("Cache load complete.");
    }

    // ── Individual refresh methods (called after each write) ───────────────────

    public void refreshLrList(Connection conn) throws SQLException {
        loadLrList(conn);
    }

    public void refreshStock(Connection conn) throws SQLException {
        loadStock(conn);
    }

    public void refreshTransfers(Connection conn) throws SQLException {
        loadTransfers(conn);
    }

    public void refreshTransports(Connection conn) throws SQLException {
        loadTransports(conn);
    }

    // ── Private loaders ────────────────────────────────────────────────────────

    private void loadTransports(Connection conn) throws SQLException {
        List<String> tmp = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM transport_companies ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) tmp.add(rs.getString("name"));
        }
        // BUG-11: always mutate ObservableList on the FXAT
        runOnFx(() -> transportNames.setAll(tmp));
    }

    private void loadLrList(Connection conn) throws SQLException {
        List<LrEntry> tmp = new ArrayList<>();
        // BUG-09: removed duplicate total_cartons / total_shop_cartons columns
        String sql = """
            SELECT e.id, e.lr_number, e.lr_date, e.transport_company,
                   COALESCE(SUM(i.cartons), 0)       AS total_cartons,
                   COALESCE(SUM(i.shop_cartons), 0)  AS total_shop_cartons
            FROM lr_entries e
            LEFT JOIN lr_items i ON i.lr_id = e.id
            WHERE e.lr_number != 'MANUAL-ADJ'
            GROUP BY e.id
            ORDER BY e.lr_date DESC, e.id DESC
            """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                LrEntry entry = new LrEntry(
                        rs.getInt("id"),
                        rs.getString("lr_number"),
                        parseDate(rs.getString("lr_date")),
                        rs.getString("transport_company")
                );
                entry.setTotalCartons(rs.getInt("total_cartons"));
                entry.setTotalShopCartons(rs.getInt("total_shop_cartons"));
                tmp.add(entry);
            }
        }
        // BUG-11: always mutate ObservableList on the FXAT
        runOnFx(() -> lrList.setAll(tmp));
    }

    private void loadStock(Connection conn) throws SQLException {
        List<StockRow> tmp = new ArrayList<>();
        String sql = """
            SELECT id, product_name, location, cartons, pairs_per_carton, lr_source, receive_date
            FROM stock
            ORDER BY product_name COLLATE NOCASE, location
            """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                tmp.add(new StockRow(
                        rs.getInt("id"),
                        rs.getString("product_name"),
                        rs.getString("location"),
                        rs.getInt("cartons"),
                        rs.getInt("pairs_per_carton"),
                        rs.getString("lr_source"),
                        rs.getString("receive_date") != null ? java.time.LocalDate.parse(rs.getString("receive_date")) : java.time.LocalDate.now()
                ));
            }
        }
        // Build suggestions from the freshly loaded list (off FXAT — no ObservableList reads)
        List<String> suggestions = buildSuggestionsFrom(tmp);
        // BUG-11: both mutations happen together on the FXAT
        runOnFx(() -> {
            stockRows.setAll(tmp);
            productSuggestions.setAll(suggestions);
        });
    }

    private void loadTransfers(Connection conn) throws SQLException {
        List<GdTransfer> tmp = new ArrayList<>();
        String sql = """
            SELECT id, transfer_date, product_name,
                   prev_location, updated_location, qty_cartons, pairs, done
            FROM gd_transfers
            ORDER BY transfer_date DESC, id DESC
            """;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                tmp.add(new GdTransfer(
                        rs.getInt("id"),
                        parseDate(rs.getString("transfer_date")),
                        rs.getString("product_name"),
                        rs.getString("prev_location"),
                        rs.getString("updated_location"),
                        rs.getInt("qty_cartons"),
                        rs.getInt("pairs"),
                        rs.getInt("done") == 1
                ));
            }
        }
        // BUG-11: always mutate ObservableList on the FXAT
        runOnFx(() -> transferList.setAll(tmp));
    }

    /**
     * Builds the product+location suggestion list from a pre-loaded stock list.
     * Pure computation — no ObservableList access, safe to call from any thread.
     * Only warehouse locations (G1–G5, RK2) are included — Shop is never added.
     */
    private List<String> buildSuggestionsFrom(List<StockRow> rows) {
        java.util.Map<String, String> dedupMap = new java.util.LinkedHashMap<>();
        for (StockRow row : rows) {
            String loc = row.getLocation();
            if (loc != null && !loc.equalsIgnoreCase("Shop")) {
                String name = row.getProductName();
                if (name != null && !name.isBlank()) {
                    String actual = name.trim() + " (" + loc.trim() + ")";
                    String key = actual.toUpperCase();
                    dedupMap.putIfAbsent(key, actual);
                }
            }
        }
        return new ArrayList<>(dedupMap.values());
    }

    // ── Thread-safe FX helper ─────────────────────────────────────────────────

    /**
     * Runs {@code r} immediately if already on the JavaFX Application Thread,
     * otherwise posts it via Platform.runLater().
     * Use for all ObservableList mutations so they are always FXAT-safe.
     */
    private static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    /**
     * Looks up a StockRow in the in-memory list for a given product+size+location.
     * Used by StockService to check if a row to merge into already exists in memory.
     */
    public StockRow findStockRow(String product, String location) {
        for (StockRow row : stockRows) {
            if (row.getProductName().equalsIgnoreCase(product)
                    && row.getLocation().equalsIgnoreCase(location)) {
                return row;
            }
        }
        return null;
    }

    /** Removes a stock row from the in-memory list by its DB id. */
    public void removeStockRowById(int id) {
        stockRows.removeIf(r -> r.getId() == id);
    }
}
