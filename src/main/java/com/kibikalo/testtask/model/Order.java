package com.kibikalo.testtask.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {
    private String id;
    private BigDecimal value;
    private List<String> promotions;

    public Order() {
        this.promotions = new ArrayList<>();
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("value")
    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    @JsonProperty("promotions")
    public List<String> getPromotions() {
        return promotions != null ? promotions : new ArrayList<>();
    }

    public void setPromotions(List<String> promotions) {
        this.promotions = promotions;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", value=" + value +
                ", promotions=" + promotions +
                '}';
    }
}