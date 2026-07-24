package com.sfw.wholesale.model;

import javafx.beans.property.*;

/**
 * Represents one item line within an LR — maps to one row in lr_items.
 */
public class LrItem {

    private int id;
    private int lrId;

    private final IntegerProperty lineNo           = new SimpleIntegerProperty(0);
    private final StringProperty  productName      = new SimpleStringProperty("");
    private final IntegerProperty cartons          = new SimpleIntegerProperty(0);
    private final IntegerProperty shopCartons      = new SimpleIntegerProperty(0);
    private final IntegerProperty pairsPerCarton   = new SimpleIntegerProperty(0);
    private final DoubleProperty  ratePerPair      = new SimpleDoubleProperty(0.0);
    private final DoubleProperty  amount           = new SimpleDoubleProperty(0.0);
    private final StringProperty  location         = new SimpleStringProperty("G1");

    // Track whether a rate has been manually entered.
    private boolean rateEntered = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    public LrItem() {
        wireAutoCalculation();
    }

    public LrItem(int id, int lrId, int lineNo, String productName,
                  int cartons, int shopCartons, int pairsPerCarton, String location) {
        this.id    = id;
        this.lrId  = lrId;
        this.lineNo.set(lineNo);
        this.productName.set(productName);
        this.cartons.set(cartons);
        this.shopCartons.set(shopCartons);
        this.pairsPerCarton.set(pairsPerCarton);
        this.location.set(location);
        wireAutoCalculation();
        recalcAmount();
    }

    // ── Property Accessors ────────────────────────────────────────────────────

    public IntegerProperty lineNoProperty()         { return lineNo; }
    public StringProperty  productNameProperty()    { return productName; }
    public IntegerProperty cartonsProperty()        { return cartons; }
    public IntegerProperty shopCartonsProperty()    { return shopCartons; }
    public IntegerProperty pairsPerCartonProperty() { return pairsPerCarton; }
    public DoubleProperty  ratePerPairProperty()    { return ratePerPair; }
    public DoubleProperty  amountProperty()         { return amount; }
    public StringProperty  locationProperty()       { return location; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int    getId()                       { return id; }
    public void   setId(int v)                  { id = v; }

    public int    getLrId()                     { return lrId; }
    public void   setLrId(int v)               { lrId = v; }

    public int    getLineNo()                   { return lineNo.get(); }
    public void   setLineNo(int v)             { lineNo.set(v); }

    public String getProductName()              { return productName.get(); }
    public void   setProductName(String v)     { productName.set(v != null ? v : ""); }

    public int    getCartons()                  { return cartons.get(); }
    public void   setCartons(int v)            { cartons.set(v); }

    public int    getShopCartons()              { return shopCartons.get(); }
    public void   setShopCartons(int v)         { shopCartons.set(v); }

    public int    getPairsPerCarton()           { return pairsPerCarton.get(); }
    public void   setPairsPerCarton(int v)     { pairsPerCarton.set(v); }

    public double getRatePerPair()              { return ratePerPair.get(); }
    public void   setRatePerPair(double v)      {
        ratePerPair.set(v);
        rateEntered = (v > 0);
        recalcAmount();
    }

    public void   clearRate()                   {
        rateEntered = false;
        ratePerPair.set(0.0);
        amount.set(0.0);
    }

    public double getAmount()                   { return amount.get(); }

    public String getLocation()                 { return location.get(); }
    public void   setLocation(String v)        { location.set(v != null ? v : "G1"); }

    public boolean isRateEntered()              { return rateEntered; }

    private void wireAutoCalculation() {
        cartons.addListener((o, ov, nv) -> recalcAmount());
        pairsPerCarton.addListener((o, ov, nv) -> recalcAmount());
        ratePerPair.addListener((o, ov, nv) -> recalcAmount());
    }

    private void recalcAmount() {
        if (rateEntered && ratePerPair.get() > 0) {
            amount.set(cartons.get() * (double) pairsPerCarton.get() * ratePerPair.get());
        } else {
            amount.set(0.0);
        }
    }

    /** Convenience: total pairs for this line. */
    public int    getTotalPairs()               { return cartons.get() * pairsPerCarton.get(); }

    @Override
    public String toString() {
        return productName.get() + " x" + cartons.get()
               + " ctns (" + shopCartons.get() + " to Shop) @ " + location.get();
    }
}
