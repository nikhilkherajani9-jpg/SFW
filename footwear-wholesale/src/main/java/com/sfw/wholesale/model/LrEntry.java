package com.sfw.wholesale.model;

import javafx.beans.property.*;
import java.time.LocalDate;

/**
 * Represents one LR (Lorry Receipt) header — maps to one row in lr_entries.
 * Uses JavaFX Properties so TableView columns can bind directly without reflection.
 */
public class LrEntry {

    private final IntegerProperty id               = new SimpleIntegerProperty(0);
    private final StringProperty  lrNumber         = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> lrDate = new SimpleObjectProperty<>(LocalDate.now());
    private final StringProperty  transportCompany = new SimpleStringProperty("");

    // Computed summary fields (aggregated from lr_items at load time — not stored in lr_entries)
    private final IntegerProperty totalCartons = new SimpleIntegerProperty(0);
    private final IntegerProperty totalShopCartons = new SimpleIntegerProperty(0);

    // ── Constructors ──────────────────────────────────────────────────────────

    public LrEntry() {}

    public LrEntry(int id, String lrNumber, LocalDate lrDate, String transport) {
        this.id.set(id);
        this.lrNumber.set(lrNumber);
        this.lrDate.set(lrDate);
        this.transportCompany.set(transport);
    }

    // ── Property Accessors ────────────────────────────────────────────────────

    public IntegerProperty idProperty()               { return id; }
    public StringProperty  lrNumberProperty()         { return lrNumber; }
    public ObjectProperty<LocalDate> lrDateProperty() { return lrDate; }
    public StringProperty  transportCompanyProperty() { return transportCompany; }
    public IntegerProperty totalCartonsProperty()     { return totalCartons; }
    public IntegerProperty totalShopCartonsProperty() { return totalShopCartons; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int         getId()               { return id.get(); }
    public void        setId(int v)          { id.set(v); }

    public String      getLrNumber()         { return lrNumber.get(); }
    public void        setLrNumber(String v) { lrNumber.set(v); }

    public LocalDate   getLrDate()           { return lrDate.get(); }
    public void        setLrDate(LocalDate v){ lrDate.set(v); }

    public String      getTransportCompany()         { return transportCompany.get(); }
    public void        setTransportCompany(String v) { transportCompany.set(v); }

    public int         getTotalCartons()         { return totalCartons.get(); }
    public void        setTotalCartons(int v)    { totalCartons.set(v); }

    public int         getTotalShopCartons()     { return totalShopCartons.get(); }
    public void        setTotalShopCartons(int v){ totalShopCartons.set(v); }

    public double      getTotalAmount()         { return 0.0; }

    @Override
    public String toString() {
        return "LrEntry{lr=" + lrNumber.get() + "}";
    }
}
