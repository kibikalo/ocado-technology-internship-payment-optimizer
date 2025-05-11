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
    private static final BigDecimal MIN_PARTIAL_POINTS_PERCENTAGE = new BigDecimal("0.10");
    private static final int SCALE = 2; // Standard scale for currency
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP; // Standard rounding mode

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

    /**
     * Calculates the effective amount to be paid for an order under a given payment strategy.
     * @param traditionalPaymentMethodId The ID of the traditional method used for payment (can be null).
     * @param amountPaidWithPoints The amount of the order value paid using points (can be BigDecimal.ZERO).
     * @return The effective amount to be paid after applying the highest applicable discount.
     *         Returns the original order value if no discount applies.
     *         Returns BigDecimal.ZERO if amountPaidWithPoints > order.getValue() or invalid method ID is provided.
     */
    public BigDecimal calculateEffectiveOrderValue(Order order, String traditionalPaymentMethodId, BigDecimal amountPaidWithPoints) {
        if (amountPaidWithPoints == null || amountPaidWithPoints.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount paid with points must be non-negative.");
        }

        if (amountPaidWithPoints.compareTo(order.getValue()) > 0) {
            System.err.println("Warning: Amount paid with points exceeds order value in effective value calculation for Order ID: " + order.getId());
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }

        BigDecimal orderValue = order.getValue();
        BigDecimal appliedDiscount = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);

        // Scenario 1: Full traditional payment
        if (amountPaidWithPoints.compareTo(BigDecimal.ZERO) == 0 && traditionalPaymentMethodId != null && !traditionalPaymentMethodId.trim().isEmpty()) {
            // Ensure the traditional method exists
            if (paymentMethods.containsKey(traditionalPaymentMethodId) && !POINTS_PAYMENT_METHOD_ID.equals(traditionalPaymentMethodId)) {
                // This check is important because full traditional payment discount EXCLUDES partial points discount.
                // We only consider the traditional discount here.
                BigDecimal traditionalDiscount = calculateFullTraditionalPaymentDiscount(order, traditionalPaymentMethodId);
                appliedDiscount = traditionalDiscount;
            } else {
                System.err.println("Warning: Invalid traditional payment method ID in effective value calculation: " + traditionalPaymentMethodId);
                return orderValue;
            }

        } else if (amountPaidWithPoints.compareTo(BigDecimal.ZERO) > 0) {
            // Scenarios 2 & 3: Full or partial points payment
            BigDecimal pointsDiscount = calculatePointsDiscount(order, amountPaidWithPoints);
            appliedDiscount = pointsDiscount;

            // IMPORTANT: Even if amountPaidWithPoints is > 0, we need to check if a traditional
            // method is also specified for partial payment. If so, we ensure it's a valid method
            // for later processing, though it doesn't affect the *discount* calculation here.
            if (traditionalPaymentMethodId != null && !traditionalPaymentMethodId.trim().isEmpty() && !paymentMethods.containsKey(traditionalPaymentMethodId)) {
                System.err.println("Warning: Invalid traditional payment method ID provided for partial points payment in effective value calculation: " + traditionalPaymentMethodId);
                // You might want to return the original value or handle this as an error
                return orderValue; // Treat as no discount if the partial traditional method is invalid
            }


        } else {
            // Scenario 4: No points, and no traditional method specified for full payment (or invalid method)
            System.out.println("Info: No points used and no valid full traditional payment method specified for Order ID: " + order.getId() + ". No discount applied.");
            return orderValue;
        }

        // The effective amount to pay is the original value minus the applied discount
        BigDecimal effectiveValue = orderValue.subtract(appliedDiscount).setScale(SCALE, ROUNDING_MODE);

        // Ensure effective value is not negative due to rounding issues (shouldn't happen with correct logic)
        if (effectiveValue.compareTo(BigDecimal.ZERO) < 0) {
            effectiveValue = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }

        return effectiveValue;
    }

    public Map<String, BigDecimal> getTotalAmountPaidByMethod() {
        // Return a copy to prevent external modification
        return new HashMap<>(totalAmountPaidByMethod);
    }
}