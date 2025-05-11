package com.kibikalo.testtask.service;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class PaymentProcessor {

    private final Map<String, PaymentMethod> paymentMethods;
    private final Map<String, BigDecimal> totalAmountPaidByMethod;

    public PaymentProcessor(Map<String, PaymentMethod> paymentMethods) {
        this.paymentMethods = paymentMethods;
        this.totalAmountPaidByMethod = new HashMap<>();
        // Initialize total amounts to zero for all payment methods
        for (String methodId : paymentMethods.keySet()) {
            totalAmountPaidByMethod.put(methodId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * Attempts to pay a specified amount for an order using a specific payment method.
     * Deducts the amount from the payment method's remaining limit and tracks the total spent per method.
     */
    public boolean processPayment(Order order, String paymentMethodId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount to pay must be non-negative.");
        }

        PaymentMethod paymentMethod = paymentMethods.get(paymentMethodId);
        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method not found with ID: " + paymentMethodId);
        }

        boolean success = paymentMethod.useAmount(amount);

        if (success) {
            BigDecimal currentTotal = totalAmountPaidByMethod.get(paymentMethodId);
            if (currentTotal == null) {
                // This should not happen if initialized correctly, just a safeguard
                currentTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            totalAmountPaidByMethod.put(paymentMethodId, currentTotal.add(amount).setScale(2, RoundingMode.HALF_UP));
        }

        return success;
    }

    public Map<String, BigDecimal> getTotalAmountPaidByMethod() {
        // Return a copy to prevent external modification
        return new HashMap<>(totalAmountPaidByMethod);
    }
}