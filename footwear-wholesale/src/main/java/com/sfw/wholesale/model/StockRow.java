package com.sfw.wholesale.model;

import javafx.beans.property.*;
import java.time.LocalDate;

/**
 * Represents one row in the stock table — a unique (product_name, size, location) combination.
 * Location is always a warehouse (G1–G5 or RK2); Shop never appears here.
 * total_pairs is computed: cartons × pairsPerCarton.
 */
public class StockRow {

    private final IntegerProperty id             = new SimpleIntegerProperty(0);
    private final StringProperty  productName    = new SimpleStringProperty("");
    private final StringProperty  location       = new SimpleStringProperty("");
    private final IntegerProperty cartons        = new SimpleIntegerProperty(0);
    private final IntegerProperty pairsPerCarton = new SimpleIntegerProperty(0);
    private final IntegerProperty totalPairs     = new SimpleIntegerProperty(0);
    private final StringProperty  lrSource       = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> receiveDate = new SimpleObjectProperty<>();
    private final BooleanProperty manualTotal = new SimpleBooleanProperty(false);

    // ── Constructors ──────────────────────────────────────────────────────────

    public StockRow() {
        wireTotalPairs();
    }

    public StockRow(int id, String productName, String location,
                    int cartons, int pairsPerCarton, String lrSource, LocalDate receiveDate) {
        this.id.set(id);
        this.productName.set(productName);
        this.location.set(location);
        this.cartons.set(cartons);
        this.pairsPerCarton.set(pairsPerCarton);
        this.lrSource.set(lrSource != null ? lrSource : "");
        this.receiveDate.set(receiveDate);
        wireTotalPairs();
        recalcTotal();
    }

    // Legacy constructor for tests or places that don't pass date yet
    public StockRow(int id, String productName, String location,
                    int cartons, int pairsPerCarton, String lrSource) {
        this(id, productName, location, cartons, pairsPerCarton, lrSource, LocalDate.now());
    }

    private void wireTotalPairs() {
        cartons.addListener((o, ov, nv) -> recalcTotal());
        pairsPerCarton.addListener((o, ov, nv) -> recalcTotal());
    }

    private void recalcTotal() {
        if (!manualTotal.get()) {
            totalPairs.set(cartons.get() * pairsPerCarton.get());
        }
    }

    // ── Property Accessors ────────────────────────────────────────────────────

    public IntegerProperty idProperty()             { return id; }
    public StringProperty  productNameProperty()    { return productName; }
    public StringProperty  locationProperty()       { return location; }
    public IntegerProperty cartonsProperty()        { return cartons; }
    public IntegerProperty pairsPerCartonProperty() { return pairsPerCarton; }
    public IntegerProperty totalPairsProperty()     { return totalPairs; }
    public StringProperty  lrSourceProperty()       { return lrSource; }
    public ObjectProperty<LocalDate> receiveDateProperty() { return receiveDate; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getId()                       { return id.get(); }
    public void   setId(int v)                  { id.set(v); }

    public String getProductName()              { return productName.get(); }
    public void   setProductName(String v)     { productName.set(v); }

    public String getLocation()                 { return location.get(); }
    public void   setLocation(String v)        { location.set(v); }

    public int    getCartons()                  { return cartons.get(); }
    public void   setCartons(int v)            { cartons.set(v); recalcTotal(); }

    public int    getPairsPerCarton()           { return pairsPerCarton.get(); }
    public void   setPairsPerCarton(int v)     { pairsPerCarton.set(v); recalcTotal(); }

    public int    getTotalPairs()               { return totalPairs.get(); }
    public void   setTotalPairs(int v)          { manualTotal.set(true); totalPairs.set(v); }

    public String getLrSource()                 { return lrSource.get(); }
    public void   setLrSource(String v)        { lrSource.set(v != null ? v : ""); }

    public LocalDate getReceiveDate()           { return receiveDate.get(); }
    public void      setReceiveDate(LocalDate v){ receiveDate.set(v); }

    @Override
    public String toString() {
        return productName.get() + " @ " + location.get()
               + " [" + cartons.get() + " ctns]";
    }
}
