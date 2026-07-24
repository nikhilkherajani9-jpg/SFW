package com.sfw.wholesale.model;

import java.time.LocalDate;

/**
 * Represents a specific deduction of cartons from a specific historical stock row.
 * Used to exactly reverse stock movements later.
 */
public class StockDeduction {
    private final String lrSource;
    private final LocalDate receiveDate;
    private final int cartons;

    public StockDeduction(String lrSource, LocalDate receiveDate, int cartons) {
        this.lrSource = lrSource;
        this.receiveDate = receiveDate;
        this.cartons = cartons;
    }

    public String getLrSource() {
        return lrSource;
    }

    public LocalDate getReceiveDate() {
        return receiveDate;
    }

    public int getCartons() {
        return cartons;
    }
}
