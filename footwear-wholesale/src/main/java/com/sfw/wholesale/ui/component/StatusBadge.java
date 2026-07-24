package com.sfw.wholesale.ui.component;

import javafx.scene.control.Label;

/**
 * A coloured pill-shaped badge Label for status display.
 *
 * StatusBadge.forLrStatus("BILL_PENDING") → red badge "● Bill Pending"
 * StatusBadge.forLrStatus("BILL_ADDED")   → green badge "✓ Bill Added"
 * StatusBadge.forBillType("CASH")         → blue badge "CASH"
 * StatusBadge.forBillType("GST")          → amber badge "GST"
 */
public class StatusBadge extends Label {

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final String RED    = "#ef4444";
    private static final String GREEN  = "#10b981";
    private static final String AMBER  = "#f59e0b";
    private static final String BLUE   = "#3b82f6";
    private StatusBadge(String text, String bgColor) {
        super(text);
        setStyle(buildStyle(bgColor));
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static StatusBadge forLrStatus(String status) {
        if ("BILL_ADDED".equals(status)) {
            return new StatusBadge("✓ Bill Added", GREEN);
        } else {
            return new StatusBadge("● Bill Pending", RED);
        }
    }

    public static StatusBadge forBillType(String billType) {
        if ("CASH".equals(billType)) {
            return new StatusBadge("CASH", BLUE);
        } else if ("GST".equals(billType)) {
            return new StatusBadge("GST", AMBER);
        } else {
            return new StatusBadge("—", "#64748b");
        }
    }

    /**
     * Returns just the CSS style string — useful for setting a cell's style
     * directly rather than adding a child node.
     */
    public static String styleForLrStatus(String status) {
        String color = "BILL_ADDED".equals(status) ? GREEN : RED;
        return buildStyle(color);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static String buildStyle(String bgColor) {
        return "-fx-background-color: " + bgColor + "22;"
             + "-fx-border-color: " + bgColor + ";"
             + "-fx-border-radius: 12;"
             + "-fx-background-radius: 12;"
             + "-fx-padding: 3 10 3 10;"
             + "-fx-text-fill: " + bgColor + ";"
             + "-fx-font-size: 11px;"
             + "-fx-font-weight: bold;";
    }
}
