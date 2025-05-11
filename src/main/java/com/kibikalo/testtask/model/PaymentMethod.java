package com.kibikalo.testtask.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentMethod {
    private String id;
    private int discount;
    private BigDecimal limit;
    private BigDecimal remainingLimit;

    public PaymentMethod() {
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("discount")
    public int getDiscount() {
        return discount;
    }

    public void setDiscount(int discount) {
        this.discount = discount;
    }

    @JsonProperty("limit")
    public BigDecimal getLimit() {
        return limit;
    }

    public void setLimit(BigDecimal limit) {
        this.limit = limit;
        this.remainingLimit = limit;
    }

    public BigDecimal getRemainingLimit() {
        return remainingLimit;
    }

//    Attempts to use a specified amount from the remaining limit,
//    returns true if the amount was successfully used (within limit), false otherwise.
    public boolean useAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount to use must be non-negative.");
        }

        if (this.remainingLimit.compareTo(amount) >= 0) {
            this.remainingLimit = this.remainingLimit.subtract(amount);
            this.remainingLimit = this.remainingLimit.setScale(2, RoundingMode.HALF_UP);
            return true;
        }
        return false;
    }

//    Adds an amount back to the remaining limit
    public void addAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount to add must be non-negative.");
        }
        this.remainingLimit = this.remainingLimit.add(amount);
        this.remainingLimit = this.remainingLimit.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "PaymentMethod{" +
                "id='" + id + '\'' +
                ", discount=" + discount +
                ", limit=" + limit +
                '}';
    }
}