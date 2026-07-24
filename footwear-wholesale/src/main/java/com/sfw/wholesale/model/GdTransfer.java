package com.sfw.wholesale.model;

import javafx.beans.property.*;
import java.time.LocalDate;

/**
 * Represents one row in gd_transfers.
 * prev_location is always a warehouse; updated_location may be a warehouse or "Shop".
 */
public class GdTransfer {

    private int id;

    private final ObjectProperty<LocalDate> transferDate   = new SimpleObjectProperty<>(LocalDate.now());
    private final StringProperty            productName    = new SimpleStringProperty("");
    private final StringProperty            prevLocation   = new SimpleStringProperty("G1");
    private final StringProperty            updatedLocation = new SimpleStringProperty("G1");
    private final IntegerProperty           qtyCartons     = new SimpleIntegerProperty(1);
    private final IntegerProperty           pairs          = new SimpleIntegerProperty(0);
    private final BooleanProperty           done           = new SimpleBooleanProperty(false);

    // ── Constructors ──────────────────────────────────────────────────────────

    public GdTransfer() {}

    public GdTransfer(int id, LocalDate transferDate, String productName,
                      String prevLocation, String updatedLocation,
                      int qtyCartons, int pairs, boolean done) {
        this.id = id;
        this.transferDate.set(transferDate);
        this.productName.set(productName);
        this.prevLocation.set(prevLocation);
        this.updatedLocation.set(updatedLocation);
        this.qtyCartons.set(qtyCartons);
        this.pairs.set(pairs);
        this.done.set(done);
    }

    // ── Property Accessors ────────────────────────────────────────────────────

    public ObjectProperty<LocalDate> transferDateProperty()    { return transferDate; }
    public StringProperty            productNameProperty()     { return productName; }
    public StringProperty            prevLocationProperty()    { return prevLocation; }
    public StringProperty            updatedLocationProperty() { return updatedLocation; }
    public IntegerProperty           qtyCartonsProperty()      { return qtyCartons; }
    public IntegerProperty           pairsProperty()           { return pairs; }
    public BooleanProperty           doneProperty()            { return done; }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int        getId()                     { return id; }
    public void       setId(int v)               { id = v; }

    public LocalDate  getTransferDate()           { return transferDate.get(); }
    public void       setTransferDate(LocalDate v){ transferDate.set(v); }

    public String     getProductName()            { return productName.get(); }
    public void       setProductName(String v)   { productName.set(v); }

    public String     getPrevLocation()           { return prevLocation.get(); }
    public void       setPrevLocation(String v)  { prevLocation.set(v); }

    public String     getUpdatedLocation()        { return updatedLocation.get(); }
    public void       setUpdatedLocation(String v){ updatedLocation.set(v); }

    public int        getQtyCartons()             { return qtyCartons.get(); }
    public void       setQtyCartons(int v)       { qtyCartons.set(v); }

    public int        getPairs()                  { return pairs.get(); }
    public void       setPairs(int v)            { pairs.set(v); }

    public boolean    isDone()                    { return done.get(); }
    public void       setDone(boolean v)         { done.set(v); }

    /** Display string for the item column. */
    public String     getItemDisplay() {
        return productName.get();
    }

    @Override
    public String toString() {
        return getItemDisplay() + " " + prevLocation.get() + "→" + updatedLocation.get()
               + " x" + qtyCartons.get();
    }
}
