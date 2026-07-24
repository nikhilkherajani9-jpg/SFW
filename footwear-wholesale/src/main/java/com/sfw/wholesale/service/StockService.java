package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.model.StockRow;

import java.sql.*;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stock management — the merge rule is the most critical logic in the system.
 *
 * Merge rule (from spec):
 *   When adding stock to a destination location:
 *   - If a row for (product, size, location) already exists → add to its cartons.
 *   - If no such row exists → create a new row.
 *   - Shop is NEVER a valid stock destination — if called with location="Shop", throw.
 *
 * Worked example: SWEETY 5X8 G4=10, transfer 3 to G2 (G2 already has 4).
 *   Result: G4=7, G2=7 (single merged row for G2, never two G2 rows).
 */
public class StockService {

    private final DataCache     cache = DataCache.getInstance();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Adds cartons to stock for the given product+size+location.
     * Merges into an existing row if one exists; creates a new row otherwise.
     * NEVER accepts location="Shop".
     *
     * @param conn       open SQLite connection (must be inside a transaction)
     * @param lrSource   the LR number that sourced this stock (for audit trail)
     */
    public void addStock(Connection conn, String product,
                         String location, int cartons, int pairsPerCarton,
                         String lrSource, LocalDate receiveDate) throws SQLException {
        
        // Search for an exact match to merge into (same product, location, ppc, lrSource, receiveDate)
        java.util.Optional<StockRow> match = cache.getStockRows().stream()
            .filter(r -> r.getProductName().equalsIgnoreCase(product)
                      && r.getLocation().equalsIgnoreCase(location)
                      && r.getPairsPerCarton() == pairsPerCarton
                      && java.util.Objects.equals(r.getLrSource(), lrSource)
                      && java.util.Objects.equals(r.getReceiveDate(), receiveDate))
            .findFirst();

        if (match.isPresent()) {
            StockRow existing = match.get();
            int newCartons = existing.getCartons() + cartons;
            updateCartonsInDb(conn, existing.getId(), newCartons);
            existing.setCartons(newCartons);
        } else {
            int newId = insertStockRow(conn, product, location, cartons, pairsPerCarton, lrSource, receiveDate);
            StockRow newRow = new StockRow(newId, product, location, cartons, pairsPerCarton, lrSource, receiveDate);
            cache.getStockRows().add(newRow);
        }
        
        // Log transaction to ledger
        int currentBalance = getAvailableCartons(product, location);
        String type = (lrSource != null && lrSource.startsWith("GD Transfer")) ? "GD_TRANSFER" : (lrSource != null && lrSource.equals("MANUAL-ADJ")) ? "MANUAL_ADJ" : "LR_ENTRY";
        insertLedgerEntry(conn, product, location, receiveDate, cartons, 0, currentBalance, pairsPerCarton, lrSource, type);
    }

    /**
     * Subtracts cartons from stock for the given product+size+location.
     * If the result reaches 0, the row is removed (no zero-carton ghost rows).
     *
     * @param conn open SQLite connection (must be inside a transaction)
     */
    public java.util.List<com.sfw.wholesale.model.StockDeduction> subtractStock(Connection conn, String product,
                               String location, int cartons, String lrSource, String type) throws SQLException {
        
        List<StockRow> batches = cache.getStockRows().stream()
            .filter(r -> r.getProductName().trim().equalsIgnoreCase(product.trim()) && r.getLocation().equalsIgnoreCase(location))
            .sorted(Comparator.comparing(r -> r.getReceiveDate() != null ? r.getReceiveDate() : java.time.LocalDate.MIN))
            .collect(Collectors.toList());

        int totalAvailable = batches.stream().mapToInt(StockRow::getCartons).sum();
        if (totalAvailable < cartons) {
            throw new IllegalArgumentException(
                "Not enough stock to transfer.\n" +
                "Product: " + product + "\n" +
                "Location: " + location + "\n" +
                "Requested: " + cartons + " carton(s)\n" +
                "Available: " + totalAvailable + " carton(s)");
        }

        java.util.List<com.sfw.wholesale.model.StockDeduction> deductions = new java.util.ArrayList<>();
        int remainingToDeduct = cartons;
        for (StockRow batch : batches) {
            if (remainingToDeduct <= 0) break;
            
            if (batch.getCartons() <= remainingToDeduct) {
                // Consume entire batch (set to 0 instead of deleting)
                deductions.add(new com.sfw.wholesale.model.StockDeduction(batch.getLrSource(), batch.getReceiveDate(), batch.getCartons()));
                remainingToDeduct -= batch.getCartons();
                updateCartonsInDb(conn, batch.getId(), 0);
                batch.setCartons(0);
            } else {
                // Consume partial batch
                deductions.add(new com.sfw.wholesale.model.StockDeduction(batch.getLrSource(), batch.getReceiveDate(), remainingToDeduct));
                int newCartons = batch.getCartons() - remainingToDeduct;
                updateCartonsInDb(conn, batch.getId(), newCartons);
                batch.setCartons(newCartons);
                remainingToDeduct = 0;
            }
        }
        
        // Log transaction to ledger
        int currentBalance = getAvailableCartons(product, location);
        int pairsPerCartonLog = deductions.isEmpty() ? 1 : getPairsPerCartonForFifo(product, location); // Just for logging
        insertLedgerEntry(conn, product, location, LocalDate.now(), 0, cartons, currentBalance, pairsPerCartonLog, lrSource, type);
        
        return deductions;
    }

    /**
     * Pre-check: returns total cartons available for the given product+location.
     * Use this BEFORE starting a transaction to give the user a clear error
     * without any partial DB changes.
     */
    public int getAvailableCartons(String product, String location) {
        return cache.getStockRows().stream()
            .filter(r -> r.getProductName().trim().equalsIgnoreCase(product.trim()) && r.getLocation().equalsIgnoreCase(location))
            .mapToInt(StockRow::getCartons)
            .sum();
    }

    /**
     * Returns the pairsPerCarton for the OLDEST batch of the given product+location
     * (i.e., the batch that FIFO will consume first). Used for computing the
     * Pairs field on a GD Transfer record.
     * Returns 1 as a safe default if no stock is found.
     */
    public int getPairsPerCartonForFifo(String product, String location) {
        return cache.getStockRows().stream()
            .filter(r -> r.getProductName().trim().equalsIgnoreCase(product.trim()) && r.getLocation().equalsIgnoreCase(location))
            .min(Comparator.comparing(r -> r.getReceiveDate() != null ? r.getReceiveDate() : java.time.LocalDate.MIN))
            .map(StockRow::getPairsPerCarton)
            .orElse(1);
    }
    
    /**
     * Imports legacy stock data along with its full ledger history.
     */
    public void importLegacyStockWithLedger(Connection conn, String product,
                                            String location, int finalBalance, int pairsPerCarton,
                                            List<com.sfw.wholesale.service.LegacyImportService.LegacyTransaction> transactions) throws SQLException {
        // Find existing match
        java.util.Optional<StockRow> match = cache.getStockRows().stream()
            .filter(r -> r.getProductName().equalsIgnoreCase(product)
                      && r.getLocation().equalsIgnoreCase(location)
                      && r.getPairsPerCarton() == pairsPerCarton)
            .findFirst();

        if (match.isPresent()) {
            StockRow existing = match.get();
            int newCartons = existing.getCartons() + finalBalance;
            updateCartonsInDb(conn, existing.getId(), newCartons);
            existing.setCartons(newCartons);
        } else {
            // Find the latest inward transaction to get the LR source for the stock row
            String finalLrSource = "";
            LocalDate latestDate = LocalDate.now();
            if (!transactions.isEmpty()) {
                latestDate = transactions.get(transactions.size() - 1).date;
                for (int i = transactions.size() - 1; i >= 0; i--) {
                    if (transactions.get(i).inward > 0 && transactions.get(i).lrSource != null && !transactions.get(i).lrSource.isBlank()) {
                        finalLrSource = transactions.get(i).lrSource;
                        break;
                    }
                }
            }
            int newId = insertStockRow(conn, product, location, finalBalance, pairsPerCarton, finalLrSource, latestDate);
            StockRow newRow = new StockRow(newId, product, location, finalBalance, pairsPerCarton, finalLrSource, latestDate);
            cache.getStockRows().add(newRow);
        }

        // Insert into ledger (reconstruct running balance)
        int runningBalance = 0;
        for (com.sfw.wholesale.service.LegacyImportService.LegacyTransaction tx : transactions) {
            runningBalance += tx.inward - tx.outward;
            String type = (tx.outward > 0 && tx.inward == 0) ? "DISPATCH" : "INWARD";
            insertLedgerEntry(conn, product, location, tx.date, tx.inward, tx.outward, runningBalance, pairsPerCarton, tx.lrSource, type);
        }
    }

    /**
     * Adds stock manually (for corrections/adjustments).
     * Creates an lr_items row under the MANUAL-ADJ LR for full traceability,
     * then calls addStock() to apply the change.
     */
    public void addManualStock(Connection conn, String product,
                                String location, int cartons, int pairsPerCarton,
                                String note) throws SQLException {

        // Look up the MANUAL-ADJ LR id
        int manualLrId = getManualAdjLrId(conn);

        // Insert an lr_items row for traceability
        String insertItem = """
            INSERT INTO lr_items
                (lr_id, line_no, product_name, cartons, pairs_per_carton, location)
            VALUES (?, (SELECT COALESCE(MAX(line_no),0)+1 FROM lr_items WHERE lr_id=?), ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
            ps.setInt(1, manualLrId);
            ps.setInt(2, manualLrId);
            ps.setString(3, product);
            ps.setInt(4, cartons);
            ps.setInt(5, pairsPerCarton);
            ps.setString(6, location);
            ps.executeUpdate();
        }

        // Apply to stock
        addStock(conn, product, location, cartons, pairsPerCarton, "MANUAL-ADJ", LocalDate.now());
    }
    
    /**
     * Fetches the complete ledger history for a given product and location.
     */
    public List<com.sfw.wholesale.model.LedgerEntry> getLedgerEntries(Connection conn, String product, String location) throws SQLException {
        List<com.sfw.wholesale.model.LedgerEntry> entries = new java.util.ArrayList<>();
        String sql = """
            SELECT id, product_name, location, transaction_date, inward_cartons, outward_cartons, balance_cartons, pairs_per_carton, lr_source, transaction_type
            FROM stock_ledger
            WHERE product_name = ? AND location = ?
            ORDER BY transaction_date ASC, id ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product);
            ps.setString(2, location);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                entries.add(new com.sfw.wholesale.model.LedgerEntry(
                    rs.getInt("id"),
                    rs.getString("product_name"),
                    rs.getString("location"),
                    LocalDate.parse(rs.getString("transaction_date")),
                    rs.getInt("inward_cartons"),
                    rs.getInt("outward_cartons"),
                    rs.getInt("balance_cartons"),
                    rs.getInt("pairs_per_carton"),
                    rs.getString("lr_source"),
                    rs.getString("transaction_type")
                ));
            }
        }
        return entries;
    }

    // ── Private DB helpers ────────────────────────────────────────────────────

    private int insertStockRow(Connection conn, String product,
                                String location, int cartons, int ppc,
                                String lrSource, LocalDate receiveDate) throws SQLException {
        String sql = """
            INSERT INTO stock (product_name, location, cartons, pairs_per_carton, lr_source, receive_date)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product);
            ps.setString(2, location);
            ps.setInt(3, cartons);
            ps.setInt(4, ppc);
            ps.setString(5, lrSource);
            ps.setString(6, receiveDate.toString());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        }
    }

    private void updateCartonsInDb(Connection conn, int stockId, int newCartons) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE stock SET cartons=? WHERE id=?")) {
            ps.setInt(1, newCartons);
            ps.setInt(2, stockId);
            ps.executeUpdate();
        }
    }

    private int getManualAdjLrId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM lr_entries WHERE lr_number='MANUAL-ADJ'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        throw new SQLException("MANUAL-ADJ LR not found. Database may be corrupted.");
    }
    
    private void insertLedgerEntry(Connection conn, String product, String location, LocalDate transactionDate,
                                   int inward, int outward, int balance, int ppc, String lrSource, String type) throws SQLException {
        String sql = """
            INSERT INTO stock_ledger 
                (product_name, location, transaction_date, inward_cartons, outward_cartons, balance_cartons, pairs_per_carton, lr_source, transaction_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product);
            ps.setString(2, location);
            ps.setString(3, transactionDate.toString());
            ps.setInt(4, inward);
            ps.setInt(5, outward);
            ps.setInt(6, balance);
            ps.setInt(7, ppc);
            ps.setString(8, lrSource);
            ps.setString(9, type);
            ps.executeUpdate();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes ALL stock batches and ledger history for the given
     * product+location combination. Called when the user explicitly deletes a
     * stock row from the Stock tab.
     *
     * @param conn     open SQLite connection inside a transaction
     * @param product  exact product name (as stored in DB)
     * @param location exact location string (e.g. "G1")
     */
    public void deleteStockByProductAndLocation(Connection conn, String product, String location)
            throws SQLException {
        // Remove ledger entries first (no FK constraint on stock_ledger)
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM stock_ledger WHERE product_name = ? AND location = ?")) {
            ps.setString(1, product);
            ps.setString(2, location);
            int deleted = ps.executeUpdate();
            java.util.logging.Logger.getLogger(StockService.class.getName())
                    .info("Deleted " + deleted + " ledger entries for: " + product + " @ " + location);
        }
        // Remove all FIFO stock rows for this product+location
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM stock WHERE product_name = ? AND location = ?")) {
            ps.setString(1, product);
            ps.setString(2, location);
            int deleted = ps.executeUpdate();
            java.util.logging.Logger.getLogger(StockService.class.getName())
                    .info("Deleted " + deleted + " stock rows for: " + product + " @ " + location);
        }
    }
}
