package com.kibikalo.testtask.service;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class PaymentProcessor {

    private final Map<String, PaymentMethod> paymentMethods;
    private final Map<String, BigDecimal> totalAmountPaidByMethod;

    // Constants for discount logic
    private static final String POINTS_PAYMENT_METHOD_ID = "PUNKTY";
    private static final BigDecimal MIN_PARTIAL_POINTS_PERCENTAGE = new BigDecimal("0.10"); // 10%

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

    /**
     * Calculates the potential discount for an order if paid fully by a specific traditional payment method.
     * @return The calculated discount amount, or BigDecimal.ZERO if no applicable discount.
     */
    public BigDecimal calculateFullTraditionalPaymentDiscount(Order order, String paymentMethodId) {
        if (paymentMethodId == null || paymentMethodId.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method ID cannot be null or empty.");
        }
        if (POINTS_PAYMENT_METHOD_ID.equals(paymentMethodId)) {
            throw new IllegalArgumentException("Cannot use traditional payment calculation for POINTS method.");
        }

        PaymentMethod paymentMethod = paymentMethods.get(paymentMethodId);
        if (paymentMethod == null) {
            System.err.println("Warning: Attempted to calculate discount for unknown method ID: " + paymentMethodId);
            return BigDecimal.ZERO;
        }

        // Check if the order's promotions list contains this payment method's ID
        List<String> applicablePromotions = order.getPromotions();
        if (applicablePromotions != null && applicablePromotions.contains(paymentMethodId)) {
            // Discount applies for full payment with this method
            BigDecimal discountPercentage = new BigDecimal(paymentMethod.getDiscount()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal discountAmount = order.getValue().multiply(discountPercentage).setScale(2, RoundingMode.HALF_UP);
            return discountAmount;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Calculates the potential discount for an order based on payment with points.
     * Handles both partial (>10%) and full points payments.
     * @return The calculated discount amount.
     */
    public BigDecimal calculatePointsDiscount(Order order, BigDecimal amountPaidWithPoints) {
        if (amountPaidWithPoints == null || amountPaidWithPoints.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount paid with points must be non-negative.");
        }
        if (amountPaidWithPoints.compareTo(order.getValue()) > 0) {
            throw new IllegalArgumentException("Amount paid with points cannot exceed order value.");
        }

        // Checks if paid entirely with points
        if (amountPaidWithPoints.compareTo(order.getValue()) == 0 && amountPaidWithPoints.compareTo(BigDecimal.ZERO) > 0) {
            // Rule 4: If entire order is paid with points, use the PUNKTY method discount
            PaymentMethod pointsMethod = paymentMethods.get(POINTS_PAYMENT_METHOD_ID);
            if (pointsMethod != null) {
                BigDecimal discountPercentage = new BigDecimal(pointsMethod.getDiscount()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal discountAmount = order.getValue().multiply(discountPercentage).setScale(2, RoundingMode.HALF_UP);
                return discountAmount;
            } else {
                // Should not happen if PUNKTY method is always provided, but handle defensively
                System.err.println("Warning: POINTS payment method not found for full points payment.");
                return BigDecimal.ZERO;
            }

        } else if (amountPaidWithPoints.compareTo(BigDecimal.ZERO) > 0) {
            // Rule 3: If client pays at least 10% with points (but not fully)
            BigDecimal minPartialPointsAmount = order.getValue().multiply(MIN_PARTIAL_POINTS_PERCENTAGE);

            if (amountPaidWithPoints.compareTo(minPartialPointsAmount) >= 0) {
                BigDecimal discountPercentage = new BigDecimal("10").divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal discountAmount = order.getValue().multiply(discountPercentage).setScale(2, RoundingMode.HALF_UP);
                return discountAmount;
            }
        }

        return BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> getTotalAmountPaidByMethod() {
        // Return a copy to prevent external modification
        return new HashMap<>(totalAmountPaidByMethod);
    }
}