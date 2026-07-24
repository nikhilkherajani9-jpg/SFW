package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import com.sfw.wholesale.model.GdTransfer;

import java.sql.*;
import java.time.LocalDate;
import java.util.logging.Logger;

/**
 * GD Transfer business logic.
 *
 * Critical rules (enforced):
 *   1. ALWAYS validate stock BEFORE entering any transaction.
 *   2. ALWAYS subtract FIRST, then add. If source fails, destination is never touched.
 *   3. ALWAYS refresh caches from DB after every operation (success OR rollback).
 *
 * Shop transfer rule: when updated_location = "Shop", subtract from source
 * warehouse only — do NOT create or update any stock row for Shop.
 */
public class GdTransferService {

    private static final Logger LOG      = Logger.getLogger(GdTransferService.class.getName());
    private final DatabaseManager db     = DatabaseManager.getInstance();
    private final DataCache       cache  = DataCache.getInstance();
    private final StockService    stockSvc = new StockService();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves a transfer record WITHOUT applying any stock movement.
     * Stock movements only happen via markDone().
     */
    public void saveTransfer(GdTransfer transfer) throws SQLException {
        // Plain save: validate fields only, no stock movement
        validateTransfer(transfer);
        try {
            db.inTransaction(conn -> {
                if (transfer.getId() == 0) {
                    insertTransfer(conn, transfer);
                } else {
                    updateTransfer(conn, transfer);
                }
            });
        } finally {
            refreshCaches();
        }
    }

    /**
     * Marks a transfer as Done and applies stock movement atomically.
     *
     * Order (never change):
     *   0. Pre-validate available stock BEFORE opening any transaction.
     *   1. SUBTRACT from source (fail-fast — if this fails, nothing else runs).
     *   2. ADD to destination (only if warehouse, not Shop).
     *   3. Persist done=1 and accurate pairs count.
     * Cache is always refreshed from DB at the end.
     */
    public void markDone(GdTransfer transfer) throws SQLException {
        if (transfer.isDone()) return; // already done — no-op

        // ── Step 0: Pre-validate BEFORE touching DB ────────────────────────
        validateTransfer(transfer);
        int available = stockSvc.getAvailableCartons(
                transfer.getProductName(), transfer.getPrevLocation());
        if (available < transfer.getQtyCartons()) {
            throw new IllegalArgumentException(
                "Not enough stock to complete this transfer.\n\n" +
                "Product:   " + transfer.getProductName() + "\n" +
                "From:      " + transfer.getPrevLocation() + "\n" +
                "Requested: " + transfer.getQtyCartons() + " carton(s)\n" +
                "Available: " + available + " carton(s)\n\n" +
                "Check the Stock tab to verify current inventory.");
        }

        // Resolve pairsPerCarton from FIFO batch BEFORE the transaction mutates stock
        int ppc = stockSvc.getPairsPerCartonForFifo(
                transfer.getProductName(), transfer.getPrevLocation());

        try {
            db.inTransaction(conn -> {
                // ── Step 1: Ensure transfer exists in DB FIRST ────────────
                if (transfer.getId() == 0) {
                    insertTransfer(conn, transfer);
                } else {
                    updateTransfer(conn, transfer);
                }

                // ── Step 2: SUBTRACT FIRST ────────────────────────────────
                String subtractLrSource = transfer.getUpdatedLocation().equalsIgnoreCase("Shop") 
                        ? "" 
                        : "Shifted from " + transfer.getPrevLocation() + " to " + transfer.getUpdatedLocation();
                String subtractType = transfer.getUpdatedLocation().equalsIgnoreCase("Shop") ? "DISPATCH" : "GD_TRANSFER";

                java.util.List<com.sfw.wholesale.model.StockDeduction> deductions = stockSvc.subtractStock(conn,
                        transfer.getProductName(),
                        transfer.getPrevLocation(),
                        transfer.getQtyCartons(),
                        subtractLrSource,
                        subtractType);
                        
                // Save deductions to the DB
                try (java.sql.PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO gd_transfer_deductions (transfer_id, lr_source, receive_date, cartons) VALUES (?, ?, ?, ?)")) {
                    for (com.sfw.wholesale.model.StockDeduction d : deductions) {
                        ps.setInt(1, transfer.getId());
                        ps.setString(2, d.getLrSource());
                        ps.setString(3, d.getReceiveDate() != null ? d.getReceiveDate().toString() : java.time.LocalDate.now().toString());
                        ps.setInt(4, d.getCartons());
                        ps.executeUpdate();
                    }
                }

                // ── Step 3: ADD to destination (skip if Shop) ─────────────
                if (!transfer.getUpdatedLocation().equalsIgnoreCase("Shop")) {
                    stockSvc.addStock(conn,
                            transfer.getProductName(),
                            transfer.getUpdatedLocation(),
                            transfer.getQtyCartons(),
                            ppc,
                            "Shifted from " + transfer.getPrevLocation() + " to " + transfer.getUpdatedLocation(),
                            transfer.getTransferDate() != null
                                    ? transfer.getTransferDate() : LocalDate.now());
                }

                // ── Step 4: Persist done flag and accurate pairs ──────────
                int accuratePairs = transfer.getQtyCartons() * ppc;
                transfer.setDone(true);
                transfer.setPairs(accuratePairs);
                updateDoneAndPairs(conn, transfer.getId(), true, accuratePairs);
            });
        } finally {
            // ALWAYS refresh: if rollback occurred, cache must match DB state
            refreshCaches();
        }
    }

    /**
     * Reverses a transfer (undoes its stock movement) and marks it not-Done.
     *
     * Order (never change):
     *   0. Pre-validate: destination warehouse must still have the stock.
     *   1. SUBTRACT from destination (fail-fast).
     *   2. ADD back to original source.
     *   3. Persist done=0.
     */
    public void undoDone(GdTransfer transfer) throws SQLException {
        if (!transfer.isDone()) return; // not done — no-op
        preValidateUndo(transfer);
        int ppc = resolvePpcForUndo(transfer);
        try {
            db.inTransaction(conn -> applyUndo(conn, transfer, ppc));
        } finally {
            refreshCaches();
        }
    }

    public void deleteTransfer(GdTransfer transfer) throws SQLException {
        if (transfer.isDone()) preValidateUndo(transfer);
        int ppc = transfer.isDone() ? resolvePpcForUndo(transfer) : 1;
        
        try {
            db.inTransaction(conn -> {
                // Undo the stock movement first safely in the same transaction
                if (transfer.isDone()) {
                    applyUndo(conn, transfer, ppc);
                }
                
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM gd_transfers WHERE id=?")) {
                    ps.setInt(1, transfer.getId());
                    ps.executeUpdate();
                }
            });
        } finally {
            refreshCaches();
        }
    }

    private void preValidateUndo(GdTransfer transfer) {
        String dst = transfer.getUpdatedLocation();
        if (!dst.equalsIgnoreCase("Shop")) {
            int available = stockSvc.getAvailableCartons(transfer.getProductName(), dst);
            if (available < transfer.getQtyCartons()) {
                throw new IllegalArgumentException(
                    "Cannot reverse this transfer.\n" +
                    "The destination stock may have already been transferred or adjusted.\n\n" +
                    "Product:   " + transfer.getProductName() + "\n" +
                    "From:      " + dst + "\n" +
                    "Expected:  " + transfer.getQtyCartons() + " carton(s)\n" +
                    "Available: " + available + " carton(s)");
            }
        }
    }

    private int resolvePpcForUndo(GdTransfer transfer) {
        if (transfer.getPairs() > 0 && transfer.getQtyCartons() > 0) {
            return transfer.getPairs() / transfer.getQtyCartons();
        } else {
            return stockSvc.getPairsPerCartonForFifo(transfer.getProductName(), transfer.getPrevLocation());
        }
    }

    private void applyUndo(Connection conn, GdTransfer transfer, int ppc) throws SQLException {
        String dst = transfer.getUpdatedLocation();
        
        // ── Step 1: SUBTRACT from destination first ────────────────
        if (!dst.equalsIgnoreCase("Shop")) {
            stockSvc.subtractStock(conn,
                    transfer.getProductName(), dst, transfer.getQtyCartons(),
                    "Reverted Transfer " + transfer.getId(), "REVERT");
        }

        // ── Step 2: ADD back to original source ───────────────────
        java.util.List<com.sfw.wholesale.model.StockDeduction> deductions = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "SELECT lr_source, receive_date, cartons FROM gd_transfer_deductions WHERE transfer_id=?")) {
            ps.setInt(1, transfer.getId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deductions.add(new com.sfw.wholesale.model.StockDeduction(
                            rs.getString("lr_source"),
                            LocalDate.parse(rs.getString("receive_date")),
                            rs.getInt("cartons")));
                }
            }
        }

        if (!deductions.isEmpty()) {
            for (com.sfw.wholesale.model.StockDeduction d : deductions) {
                stockSvc.addStock(conn,
                        transfer.getProductName(),
                        transfer.getPrevLocation(),
                        d.getCartons(),
                        ppc,
                        d.getLrSource(),
                        d.getReceiveDate());
            }
            // Clean up the deductions table
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM gd_transfer_deductions WHERE transfer_id=?")) {
                ps.setInt(1, transfer.getId());
                ps.executeUpdate();
            }
        } else {
            // Legacy fallback
            com.sfw.wholesale.model.StockRow existing = cache.getStockRows().stream()
                    .filter(r -> r.getProductName().equalsIgnoreCase(transfer.getProductName()) 
                              && r.getLocation().equalsIgnoreCase(transfer.getPrevLocation()))
                    .max(java.util.Comparator.comparing(r -> r.getReceiveDate() != null ? r.getReceiveDate() : LocalDate.MIN))
                    .orElse(null);

            String revertLrSource = existing != null ? existing.getLrSource() : "GD-REVERSAL-" + transfer.getId();
            LocalDate revertDate = existing != null ? existing.getReceiveDate() 
                    : (transfer.getTransferDate() != null ? transfer.getTransferDate() : LocalDate.now());

            stockSvc.addStock(conn,
                    transfer.getProductName(),
                    transfer.getPrevLocation(),
                    transfer.getQtyCartons(),
                    ppc,
                    revertLrSource,
                    revertDate);
        }

        // ── Step 3: Persist not-done ───────────────────────────────
        transfer.setDone(false);
        updateDoneAndPairs(conn, transfer.getId(), false, transfer.getPairs());
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private void insertTransfer(Connection conn, GdTransfer t) throws SQLException {
        String sql = """
            INSERT INTO gd_transfers
                (transfer_date, product_name, prev_location, updated_location,
                 qty_cartons, pairs, done)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getTransferDate().toString());
            ps.setString(2, t.getProductName());
            ps.setString(3, t.getPrevLocation());
            ps.setString(4, t.getUpdatedLocation());
            ps.setInt(5, t.getQtyCartons());
            ps.setInt(6, t.getPairs());
            ps.setInt(7, t.isDone() ? 1 : 0);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) t.setId(keys.getInt(1));
        }
    }

    private void updateTransfer(Connection conn, GdTransfer t) throws SQLException {
        String sql = """
            UPDATE gd_transfers
            SET transfer_date=?, product_name=?, prev_location=?,
                updated_location=?, qty_cartons=?, pairs=?, done=?
            WHERE id=?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getTransferDate().toString());
            ps.setString(2, t.getProductName());
            ps.setString(3, t.getPrevLocation());
            ps.setString(4, t.getUpdatedLocation());
            ps.setInt(5, t.getQtyCartons());
            ps.setInt(6, t.getPairs());
            ps.setInt(7, t.isDone() ? 1 : 0);
            ps.setInt(8, t.getId());
            ps.executeUpdate();
        }
    }

    private void updateDoneAndPairs(Connection conn, int id, boolean done, int pairs) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE gd_transfers SET done=?, pairs=? WHERE id=?")) {
            ps.setInt(1, done ? 1 : 0);
            ps.setInt(2, pairs);
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateTransfer(GdTransfer t) {
        if (t.getProductName() == null || t.getProductName().isBlank())
            throw new IllegalArgumentException("Product name is required.");
        if (t.getQtyCartons() < 1)
            throw new IllegalArgumentException("Quantity must be at least 1 carton.");
        if (t.getPrevLocation() == null || t.getPrevLocation().isBlank())
            throw new IllegalArgumentException("Source (From) location is required.");
        if (t.getUpdatedLocation() == null || t.getUpdatedLocation().isBlank())
            throw new IllegalArgumentException("Destination (To) location is required.");
        if ("Shop".equalsIgnoreCase(t.getPrevLocation()))
            throw new IllegalArgumentException(
                "Shop cannot be a source. Stock can only leave the Shop via a sale.");
        if (t.getPrevLocation().equalsIgnoreCase(t.getUpdatedLocation()))
            throw new IllegalArgumentException(
                "Source and destination must be different locations.");
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    /**
     * Always refreshes both caches from DB — after success OR rollback.
     * This guarantees in-memory state exactly matches the persisted database.
     */
    private void refreshCaches() {
        try {
            java.sql.Connection conn = db.getConnection();
            cache.refreshTransfers(conn);
            cache.refreshStock(conn);
        } catch (java.sql.SQLException e) {
            LOG.warning("Cache refresh failed: " + e.getMessage());
            javafx.application.Platform.runLater(() -> 
                com.sfw.wholesale.ui.component.ConfirmDialog.showError("Cache Error", "Could not refresh latest data — please restart the app if numbers look wrong")
            );
        }
    }
}
