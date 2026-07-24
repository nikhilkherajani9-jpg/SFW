package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.model.LrEntry;
import com.sfw.wholesale.model.LrItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * All LR business logic: save, load items, validate, and trigger downstream
 * stock and creditor updates.
 *
 * Rule: saving an LR always updates Stock immediately, even without a bill.
 * Rule: when Bill No. is added, CreditorService.upsertCreditor() is called.
 */
public class LrService {

    private final DatabaseManager db = DatabaseManager.getInstance();
    private final DataCache cache = DataCache.getInstance();
    private final StockService stockSvc = new StockService();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves (insert or update) an LR and all its item lines.
     *
     * Steps:
     * 1. Validate the entry and all items.
     * 2. Upsert lr_entries row.
     * 3. For an existing LR: reverse previous stock contribution, then re-apply new
     * items.
     * For a new LR: apply stock additions.
     * 4. Delete + re-insert all lr_items (simplest correct approach for edits).
     *
     * 5. Refresh relevant caches.
     *
     * @throws IllegalArgumentException on validation failure (message shown to
     *                                  user).
     * @throws SQLException             on database errors.
     */
    public void saveLr(LrEntry entry, List<LrItem> items) throws SQLException {
        validate(entry, items);

        db.inTransaction(conn -> {
            ensureTransport(conn, entry.getTransportCompany());

            boolean isNew = entry.getId() == 0;

            // Upsert lr_entries
            int lrId = upsertLrEntry(conn, entry);
            entry.setId(lrId);

            // Compute delta and update stock
            applyStockDelta(conn, isNew ? 0 : lrId, entry.getLrNumber(), entry.getLrDate(), items);

            // Delete old items and re-insert
            deleteItems(conn, lrId);

            int lineNo = 1;
            for (LrItem item : items) {
                item.setLrId(lrId);
                item.setLineNo(lineNo++);
                insertItem(conn, item);
            }
        });

        // Refresh caches on JavaFX thread
        Connection conn = db.getConnection();
        cache.refreshLrList(conn);
        cache.refreshStock(conn);
        cache.refreshTransports(conn);
    }

    /** Loads the full item lines for one LR (by its DB id). */
    public List<LrItem> loadItemsForLr(int lrId) throws SQLException {
        List<LrItem> result = new ArrayList<>();
        String sql = """
                SELECT id, lr_id, line_no, product_name,
                       cartons, shop_cartons, pairs_per_carton, location
                FROM lr_items
                WHERE lr_id = ?
                ORDER BY line_no
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, lrId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LrItem item = new LrItem(
                        rs.getInt("id"),
                        rs.getInt("lr_id"),
                        rs.getInt("line_no"),
                        rs.getString("product_name"),
                        rs.getInt("cartons"),
                        rs.getInt("shop_cartons"),
                        rs.getInt("pairs_per_carton"),
                        rs.getString("location"));
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Checks whether an LR number already exists (used for duplicate validation).
     */
    public boolean lrNumberExists(String lrNumber, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM lr_entries WHERE lr_number = ? AND id != ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, lrNumber);
            ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validate(LrEntry entry, List<LrItem> items) {
        if (entry.getLrNumber() == null || entry.getLrNumber().isBlank())
            throw new IllegalArgumentException("LR Number is required.");
        if (entry.getLrDate() == null)
            throw new IllegalArgumentException("Received Date is required.");
        if (entry.getTransportCompany() == null || entry.getTransportCompany().isBlank())
            throw new IllegalArgumentException("Transport Company is required.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("At least one item line is required.");

        for (int i = 0; i < items.size(); i++) {
            LrItem it = items.get(i);
            String prefix = "Line " + (i + 1) + ": ";
            if (it.getProductName() == null || it.getProductName().isBlank())
                throw new IllegalArgumentException(prefix + "Product Name is required.");
            if (it.getCartons() <= 0)
                throw new IllegalArgumentException(prefix + "Cartons must be > 0.");
            if (it.getPairsPerCarton() <= 0)
                throw new IllegalArgumentException(prefix + "Pairs per Carton must be > 0.");
            if (it.getLocation() == null || it.getLocation().isBlank())
                throw new IllegalArgumentException(prefix + "Location is required.");
            if (!isWarehouse(it.getLocation()) && !"Shop".equalsIgnoreCase(it.getLocation()))
                throw new IllegalArgumentException(prefix + "Location must be a warehouse (G1–G5 or RK2) or Shop.");
        }
    }

    private int upsertLrEntry(Connection conn, LrEntry e) throws SQLException {
        if (e.getId() == 0) {
            // INSERT
            String sql = """
                    INSERT INTO lr_entries
                        (lr_number, lr_date, transport_company)
                    VALUES (?, ?, ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, e.getLrNumber());
                ps.setString(2, e.getLrDate().toString());
                ps.setString(3, e.getTransportCompany());
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next())
                    return keys.getInt(1);
            }
        } else {
            // UPDATE
            String sql = """
                    UPDATE lr_entries
                    SET lr_number=?, lr_date=?, transport_company=?
                    WHERE id=?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getLrNumber());
                ps.setString(2, e.getLrDate().toString());
                ps.setString(3, e.getTransportCompany());
                ps.setInt(4, e.getId());
                ps.executeUpdate();
            }
        }
        return e.getId();
    }

    private void insertItem(Connection conn, LrItem item) throws SQLException {
        String sql = """
                INSERT INTO lr_items
                    (lr_id, line_no, product_name, cartons, shop_cartons, pairs_per_carton, location)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getLrId());
            ps.setInt(2, item.getLineNo());
            ps.setString(3, item.getProductName());
            ps.setInt(4, item.getCartons());
            ps.setInt(5, item.getShopCartons());
            ps.setInt(6, item.getPairsPerCarton());
            ps.setString(7, item.getLocation());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next())
                item.setId(keys.getInt(1));
        }
    }

    private void deleteItems(Connection conn, int lrId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM lr_items WHERE lr_id = ?")) {
            ps.setInt(1, lrId);
            ps.executeUpdate();
        }
    }



    /**
     * Delta method: Compare old LR items with new ones. Only update stock for the
     * difference.
     * This allows updating bill rates/numbers without touching stock that might
     * have been transferred.
     */
    private void applyStockDelta(Connection conn, int oldLrId, String lrNumber, java.time.LocalDate receiveDate,
            List<LrItem> newItems) throws SQLException {
        List<LrItem> oldItems = (oldLrId == 0) ? new java.util.ArrayList<>() : loadItemsForLr(oldLrId);

        // Group old items by product + location + pairsPerCarton
        java.util.Map<String, Integer> oldStock = new java.util.HashMap<>();
        for (LrItem old : oldItems) {
            String key = old.getProductName() + "::" + old.getLocation() + "::" + old.getPairsPerCarton();
            int qtyForWarehouse = old.getCartons() - old.getShopCartons();
            oldStock.put(key, oldStock.getOrDefault(key, 0) + qtyForWarehouse);
        }

        // Group new items by product + location + pairsPerCarton
        java.util.Map<String, Integer> newStock = new java.util.HashMap<>();
        for (LrItem newItem : newItems) {
            String key = newItem.getProductName() + "::" + newItem.getLocation() + "::" + newItem.getPairsPerCarton();
            int qtyForWarehouse = newItem.getCartons() - newItem.getShopCartons();
            newStock.put(key, newStock.getOrDefault(key, 0) + qtyForWarehouse);
        }

        // Calculate delta and apply
        java.util.Set<String> allKeys = new java.util.HashSet<>(oldStock.keySet());
        allKeys.addAll(newStock.keySet());

        for (String key : allKeys) {
            int oldQty = oldStock.getOrDefault(key, 0);
            int newQty = newStock.getOrDefault(key, 0);
            int delta = newQty - oldQty;

            if (delta == 0)
                continue;

            String[] parts = key.split("::");
            String product = parts[0];
            String location = parts[1];
            int pairsPerCarton = Integer.parseInt(parts[2]);

            if ("Shop".equalsIgnoreCase(location))
                continue;

            if (delta > 0) {
                stockSvc.addStock(conn, product, location, delta, pairsPerCarton, lrNumber, receiveDate);
            } else {
                stockSvc.subtractStock(conn, product, location, -delta, "LR Edit Deduction", "LR_EDIT");
            }
        }
    }

    private void ensureTransport(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO transport_companies (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private boolean isWarehouse(String loc) {
        return loc != null && (loc.equals("G1") || loc.equals("G2") || loc.equals("G3")
                || loc.equals("G4") || loc.equals("G5") || loc.equals("RK2"));
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank())
            ps.setNull(idx, Types.VARCHAR);
        else
            ps.setString(idx, val);
    }
}
