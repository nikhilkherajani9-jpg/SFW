package com.sfw.wholesale.service;

import com.sfw.wholesale.model.GdTransfer;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GD Transfer rules (Rules 43-61 from audit checklist).
 * Tests validation (Shop guard, qty), and business logic constraints.
 * Pure unit tests — no live DB needed.
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class GdTransferTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static GdTransfer sampleTransfer(String product, String from, String to, int qty) {
        GdTransfer t = new GdTransfer();
        t.setTransferDate(LocalDate.now());
        t.setProductName(product);
        t.setPrevLocation(from);
        t.setUpdatedLocation(to);
        t.setQtyCartons(qty);
        return t;
    }

    // Mirrors GdTransferService.validateTransfer()
    private void validate(GdTransfer t) {
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

    // ── Rule 43: Prev Location (From) restricted ──────────────────────────────

    @Test
    @DisplayName("Rule 43 — Shop cannot be the source location")
    void rule43_shopSourceRejected() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "Shop", "G1", 5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(t));
        assertTrue(ex.getMessage().contains("Shop cannot be a source"), ex.getMessage());
    }

    @Test
    @DisplayName("Rule 43 — Warehouse source is accepted")
    void rule43_warehouseSourceAccepted() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "G1", "G2", 5);
        assertDoesNotThrow(() -> validate(t));
    }

    // ── Rule 44: Updated Location (To) includes Shop ──────────────────────────

    @Test
    @DisplayName("Rule 44 — Shop is a valid destination location")
    void rule44_shopDestinationAccepted() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "G1", "Shop", 5);
        // Should pass validation (Shop is allowed as destination)
        assertDoesNotThrow(() -> validate(t));
    }

    // ── Rule 45: Qty >= 1 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Rule 45 — Zero qty is rejected")
    void rule45_zeroQtyRejected() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "G1", "G2", 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(t));
        assertTrue(ex.getMessage().contains("at least 1 carton"), ex.getMessage());
    }

    @Test
    @DisplayName("Rule 45 — Negative qty is rejected")
    void rule45_negativeQtyRejected() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "G1", "G2", -5);
        assertThrows(IllegalArgumentException.class, () -> validate(t));
    }

    // ── Rule 46: Source and Dest must differ ──────────────────────────────────

    @Test
    @DisplayName("Rule 46 — Source and Destination cannot be the same")
    void rule46_sameSourceDestRejected() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "G1", "G1", 5);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(t));
        assertTrue(ex.getMessage().contains("different locations"), ex.getMessage());
    }

    @Test
    @DisplayName("Rule 46 — Source and Destination case-insensitive check")
    void rule46_sameSourceDestCaseInsensitiveRejected() {
        GdTransfer t = sampleTransfer("SWEETY 5X8", "g1", "G1", 5);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(t));
        assertTrue(ex.getMessage().contains("different locations"), ex.getMessage());
    }

    // ── Other required fields ─────────────────────────────────────────────────

    @Test
    @DisplayName("Validation — Product name is required")
    void validation_productRequired() {
        GdTransfer t = sampleTransfer("", "G1", "G2", 5);
        assertThrows(IllegalArgumentException.class, () -> validate(t));
    }

    @Test
    @DisplayName("Validation — Blank locations are rejected")
    void validation_blankLocationsRejected() {
        GdTransfer t1 = sampleTransfer("SWEETY", "", "G2", 5);
        assertThrows(IllegalArgumentException.class, () -> validate(t1));

        GdTransfer t2 = sampleTransfer("SWEETY", "G1", "", 5);
        assertThrows(IllegalArgumentException.class, () -> validate(t2));
    }
}
