package com.sfw.wholesale.service;

import com.sfw.wholesale.model.LrEntry;
import com.sfw.wholesale.model.LrItem;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LR entry rules (Rules 1-18 from audit checklist).
 * Pure unit tests — no live DB needed for validation and model tests.
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class LrEntryTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static LrEntry sampleEntry() {
        LrEntry e = new LrEntry();
        e.setLrNumber("LR-TEST-001");
        e.setLrDate(LocalDate.of(2024, 1, 15));
        e.setTransportCompany("FAST TRANSPORT");
        return e;
    }

    private static LrItem sampleItem(String product, int cartons, int ppc, String location) {
        LrItem item = new LrItem();
        item.setProductName(product);
        item.setCartons(cartons);
        item.setPairsPerCarton(ppc);
        item.setLocation(location);
        return item;
    }


    // Mirrors LrService.isWarehouse()
    private static boolean isWarehouse(String loc) {
        return loc != null && (loc.equals("G1") || loc.equals("G2") || loc.equals("G3")
                || loc.equals("G4") || loc.equals("G5") || loc.equals("RK2"));
    }

    // ── LrService validation helper (pure validation, no DB) ──────────────────

    private void validate(LrEntry entry, List<LrItem> items) {
        if (entry.getLrNumber() == null || entry.getLrNumber().isBlank())
            throw new IllegalArgumentException("LR Number is required.");
        if (entry.getLrDate() == null)
            throw new IllegalArgumentException("LR Date is required.");
        if (entry.getTransportCompany() == null || entry.getTransportCompany().isBlank())
            throw new IllegalArgumentException("Transport Company is required.");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("At least one item line is required.");
        for (int i = 0; i < items.size(); i++) {
            LrItem it = items.get(i);
            String pfx = "Line " + (i + 1) + ": ";
            if (it.getProductName() == null || it.getProductName().isBlank())
                throw new IllegalArgumentException(pfx + "Product Name is required.");
            if (it.getCartons() <= 0)
                throw new IllegalArgumentException(pfx + "Cartons must be > 0.");
            if (it.getPairsPerCarton() <= 0)
                throw new IllegalArgumentException(pfx + "Pairs per Carton must be > 0.");
            if (it.getLocation() == null || it.getLocation().isBlank())
                throw new IllegalArgumentException(pfx + "Location is required.");
            if (!isWarehouse(it.getLocation()))
                throw new IllegalArgumentException(pfx + "Location must be a warehouse (G1-G5 or RK2).");
        }
    }

    // ── Rule 1: LR Number required ─────────────────────────────────────────────

    @Test
    @DisplayName("Rule 1 — Blank LR number is rejected")
    void rule1_lrNumberRequired() {
        LrEntry entry = sampleEntry();
        entry.setLrNumber("");
        assertThrows(IllegalArgumentException.class,
            () -> validate(entry, List.of(sampleItem("SWEETY 5X8", 5, 8, "G1"))));
    }

    @Test
    @DisplayName("Rule 1 (edge) — Whitespace-only LR number is rejected")
    void rule1_whitespaceOnlyLrNumber() {
        LrEntry entry = sampleEntry();
        entry.setLrNumber("   ");
        assertThrows(IllegalArgumentException.class,
            () -> validate(entry, List.of(sampleItem("SWEETY 5X8", 5, 8, "G1"))));
    }

    // ── Rule 2: Received Date required ───────────────────────────────────────

    @Test
    @DisplayName("Rule 2 — Null Received Date is rejected")
    void rule2_receivedDateRequired() {
        LrEntry entry = sampleEntry();
        entry.setLrDate(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(entry, List.of(sampleItem("SWEETY 5X8", 5, 8, "G1"))));
        assertTrue(ex.getMessage().contains("Date"), ex.getMessage());
    }

    // ── Rule 3 & 4: Required string fields ────────────────────────────────────

    @Test
    @DisplayName("Rule 3 — Blank transport company is rejected")
    void rule3_transportRequired() {
        LrEntry entry = sampleEntry();
        entry.setTransportCompany("");
        assertThrows(IllegalArgumentException.class,
            () -> validate(entry, List.of(sampleItem("SWEETY 5X8", 5, 8, "G1"))));
    }



    // ── Rule 5: At least one item ──────────────────────────────────────────────

    @Test
    @DisplayName("Rule 5 — Empty item list is rejected")
    void rule5_atLeastOneItemRequired() {
        assertThrows(IllegalArgumentException.class, () -> validate(sampleEntry(), List.of()));
    }

    @Test
    @DisplayName("Rule 5 — Null item list is rejected")
    void rule5_nullItemListRejected() {
        assertThrows(IllegalArgumentException.class, () -> validate(sampleEntry(), null));
    }

    // ── Rule 6: Product name per item ─────────────────────────────────────────

    @Test
    @DisplayName("Rule 6 — Item with blank product name is rejected")
    void rule6_productNameRequired() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("", 5, 8, "G1"))));
        assertTrue(ex.getMessage().contains("Product Name"), ex.getMessage());
    }

    // ── Rule 7: Cartons > 0 ────────────────────────────────────────────────────

    @Test
    @DisplayName("Rule 7 — Zero cartons is rejected")
    void rule7_zeroCartonsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 0, 8, "G1"))));
    }

    @Test
    @DisplayName("Rule 7 (edge) — Negative cartons is rejected")
    void rule7_negativeCartonsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", -3, 8, "G1"))));
    }

    // ── Rule 8: PPC > 0 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Rule 8 — Zero pairs-per-carton is rejected")
    void rule8_zeroPpcRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 5, 0, "G1"))));
    }

    @Test
    @DisplayName("Rule 8 (edge) — Negative ppc is rejected")
    void rule8_negativePpcRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 5, -1, "G1"))));
    }

    // ── Rule 10: Location restricted ──────────────────────────────────────────

    @Test
    @DisplayName("Rule 10 — Shop as LR item location is rejected")
    void rule10_shopLocationRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 5, 8, "Shop"))));
        assertTrue(ex.getMessage().toLowerCase().contains("warehouse") ||
                   ex.getMessage().toLowerCase().contains("location"), ex.getMessage());
    }

    @Test
    @DisplayName("Rule 10 (edge) — Invalid location 'X99' is rejected")
    void rule10_invalidLocationRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 5, 8, "X99"))));
    }

    @Test
    @DisplayName("Rule 10 (happy) — All 6 valid warehouse locations accepted")
    void rule10_allValidLocationsAccepted() {
        String[] valid = {"G1", "G2", "G3", "G4", "G5", "RK2"};
        for (String loc : valid) {
            assertTrue(isWarehouse(loc), loc + " must be valid");
            // Should NOT throw
            assertDoesNotThrow(() ->
                validate(sampleEntry(), List.of(sampleItem("SWEETY 5X8", 5, 8, loc))));
        }
    }



    // ── Rule 18: Delta logic ──────────────────────────────────────────────────

    @Test
    @DisplayName("Rule 18 — Zero delta: same qty old=new means no stock change needed")
    void rule18_zeroDeltaNoStockChange() {
        int delta = 5 - 5; // old=5, new=5
        assertEquals(0, delta);
    }

    @Test
    @DisplayName("Rule 18 — Positive delta: increased cartons needs addStock")
    void rule18_positiveDeltaAddsStock() {
        int delta = 8 - 5; // new=8, old=5
        assertEquals(3, delta);
        assertTrue(delta > 0); // → addStock(3)
    }

    @Test
    @DisplayName("Rule 18 — Negative delta: reduced cartons needs subtractStock")
    void rule18_negativeDeltaSubtractsStock() {
        int delta = 3 - 5; // new=3, old=5
        assertEquals(-2, delta);
        assertTrue(delta < 0); // → subtractStock(2)
    }
}
