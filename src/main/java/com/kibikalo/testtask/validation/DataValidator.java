package com.kibikalo.testtask.validation;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DataValidator {

    public void validateData(List<Order> orders, List<PaymentMethod> paymentMethods) {
        validateOrders(orders, paymentMethods);
        validatePaymentMethods(paymentMethods);
    }

    private void validateOrders(List<Order> orders, List<PaymentMethod> paymentMethods) {
        if (orders == null) {
            throw new IllegalArgumentException("Orders list cannot be null.");
        }

        Set<String> validPaymentMethodIds = paymentMethods.stream()
                .map(PaymentMethod::getId)
                .collect(Collectors.toSet());

        for (Order order : orders) {
            if (order.getId() == null || order.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("Order ID cannot be null or empty.");
            }

            if (order.getValue() == null) {
                throw new IllegalArgumentException("Order value cannot be null for Order ID: " + order.getId());
            }

            if (order.getValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Order value cannot be negative for Order ID: " + order.getId());
            }

            if (order.getPromotions() != null) {
                for (String promotionId : order.getPromotions()) {
                    if (!validPaymentMethodIds.contains(promotionId)) {
                        if (!"PUNKTY".equals(promotionId)) {
                            throw new IllegalArgumentException(
                                    "Unknown promotion ID '" + promotionId + "' for Order ID: " + order.getId() +
                                            "' in promotions list. Promotion must match a valid payment method ID."
                            );
                        }
                    }
                }
            }
        }
    }

    private void validatePaymentMethods(List<PaymentMethod> paymentMethods) {
        if (paymentMethods == null) {
            throw new IllegalArgumentException("Payment methods list cannot be null.");
        }

        Set<String> paymentMethodIds = new java.util.HashSet<>();

        for (PaymentMethod method : paymentMethods) {
            if (method.getId() == null || method.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("Payment method ID cannot be null or empty.");
            }

            if (!paymentMethodIds.add(method.getId())) {
                throw new IllegalArgumentException("Duplicate payment method ID found: " + method.getId());
            }

            if (method.getDiscount() < 0) {
                throw new IllegalArgumentException("Payment method discount cannot be negative for ID: " + method.getId());
            }

            if (method.getLimit() == null) {
                throw new IllegalArgumentException("Payment method limit cannot be null for ID: " + method.getId());
            }

            if (method.getLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Payment method limit cannot be negative for ID: " + method.getId());
            }
        }
    }
}