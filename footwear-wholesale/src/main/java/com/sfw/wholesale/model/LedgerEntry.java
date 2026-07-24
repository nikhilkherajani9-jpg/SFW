package com.sfw.wholesale.model;

import javafx.beans.property.*;
import java.time.LocalDate;

public class LedgerEntry {
    private final IntegerProperty id = new SimpleIntegerProperty(0);
    private final StringProperty productName = new SimpleStringProperty("");
    private final StringProperty location = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> transactionDate = new SimpleObjectProperty<>();
    private final IntegerProperty inwardCartons = new SimpleIntegerProperty(0);
    private final IntegerProperty outwardCartons = new SimpleIntegerProperty(0);
    private final IntegerProperty balanceCartons = new SimpleIntegerProperty(0);
    private final IntegerProperty pairsPerCarton = new SimpleIntegerProperty(1);
    private final StringProperty lrSource = new SimpleStringProperty("");
    private final StringProperty transactionType = new SimpleStringProperty("");

    public LedgerEntry(int id, String productName, String location, LocalDate transactionDate,
                       int inwardCartons, int outwardCartons, int balanceCartons, int pairsPerCarton,
                       String lrSource, String transactionType) {
        this.id.set(id);
        this.productName.set(productName != null ? productName : "");
        this.location.set(location != null ? location : "");
        this.transactionDate.set(transactionDate);
        this.inwardCartons.set(inwardCartons);
        this.outwardCartons.set(outwardCartons);
        this.balanceCartons.set(balanceCartons);
        this.pairsPerCarton.set(pairsPerCarton);
        this.lrSource.set(lrSource != null ? lrSource : "");
        this.transactionType.set(transactionType != null ? transactionType : "");
    }

    public int getId() { return id.get(); }
    public String getProductName() { return productName.get(); }
    public String getLocation() { return location.get(); }
    public LocalDate getTransactionDate() { return transactionDate.get(); }
    public int getInwardCartons() { return inwardCartons.get(); }
    public int getOutwardCartons() { return outwardCartons.get(); }
    public int getBalanceCartons() { return balanceCartons.get(); }
    public int getPairsPerCarton() { return pairsPerCarton.get(); }
    public String getLrSource() { return lrSource.get(); }
    public String getTransactionType() { return transactionType.get(); }

    public IntegerProperty inwardCartonsProperty() { return inwardCartons; }
    public IntegerProperty outwardCartonsProperty() { return outwardCartons; }
    public IntegerProperty balanceCartonsProperty() { return balanceCartons; }
    public IntegerProperty pairsPerCartonProperty() { return pairsPerCarton; }
    public StringProperty lrSourceProperty() { return lrSource; }
    public StringProperty transactionTypeProperty() { return transactionType; }
    public ObjectProperty<LocalDate> transactionDateProperty() { return transactionDate; }
}
