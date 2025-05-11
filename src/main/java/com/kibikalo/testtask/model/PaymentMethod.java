package com.kibikalo.testtask.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentMethod {
    private String id;
    private int discount;
    private BigDecimal limit;

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