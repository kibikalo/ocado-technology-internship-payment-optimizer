package com.kibikalo.testtask.service;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PaymentOptimizerService {

    private final PaymentProcessor paymentProcessor;
    private final Map<String, PaymentMethod> paymentMethodsMap; // Keep a reference to the payment methods

    // Constants
    private static final String POINTS_PAYMENT_METHOD_ID = "PUNKTY";
    private static final BigDecimal MIN_PARTIAL_POINTS_PERCENTAGE = new BigDecimal("0.10"); // 10%

    public PaymentOptimizerService(PaymentProcessor paymentProcessor, Map<String, PaymentMethod> paymentMethodsMap) {
        this.paymentProcessor = paymentProcessor;
        this.paymentMethodsMap = paymentMethodsMap;
    }

    /**
     * Finds and applies an optimal payment strategy for a list of orders
     * to maximize total discount, using a greedy approach.
     *
     * @param orders The list of orders to process.
     * @return A list of orders that could not be fully paid (should be empty in a successful run).
     */
    public List<Order> optimizePayments(List<Order> orders) {
        // Work on a copy of the orders list to avoid modifying the original input if needed elsewhere
        List<Order> unpaidOrders = new ArrayList<>(orders);
        List<Order> paidOrders = new ArrayList<>();

        System.out.println("\n--- Starting Payment Optimization ---");

        // Step 1: Prioritize Bank Card Discounts
        System.out.println("--- Step 1: Prioritizing Bank Card Discounts ---");
        // Filter orders that *might* be eligible for a bank discount and are currently unpaid
        List<Order> ordersWithBankPromos = unpaidOrders.stream()
                .filter(order -> order.getPromotions() != null && !order.getPromotions().isEmpty() &&
                        order.getPromotions().stream().anyMatch(promoId ->
                                !POINTS_PAYMENT_METHOD_ID.equals(promoId) && paymentMethodsMap.containsKey(promoId)))
                .collect(Collectors.toList());

        // Sort these orders by potential bank discount (descending)
        ordersWithBankPromos.sort(Comparator.comparing(this::calculateMaxBankDiscountForOrder).reversed());

        List<Order> fullyPaidInStep1 = new ArrayList<>();
        for (Order order : ordersWithBankPromos) {
            if (paidOrders.contains(order)) {
                continue; // Already paid by a previous decision
            }

            BigDecimal bestDiscount = BigDecimal.ZERO;
            String bestMethodId = null;

            // Find the best applicable bank card discount for this specific order
            if (order.getPromotions() != null) {
                for (String promoId : order.getPromotions()) {
                    if (!POINTS_PAYMENT_METHOD_ID.equals(promoId) && paymentMethodsMap.containsKey(promoId)) {
                        BigDecimal currentDiscount = paymentProcessor.calculateFullTraditionalPaymentDiscount(order, promoId);
                        if (currentDiscount.compareTo(bestDiscount) > 0) {
                            bestDiscount = currentDiscount;
                            bestMethodId = promoId;
                        }
                    }
                }
            }

            // If a beneficial bank discount is found, check feasibility and apply
            if (bestMethodId != null && bestDiscount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal effectiveValue = order.getValue().subtract(bestDiscount);
                PaymentMethod paymentMethod = paymentMethodsMap.get(bestMethodId);

                // Check if the method has enough remaining limit
                if (paymentMethod != null && paymentMethod.getRemainingLimit().compareTo(effectiveValue) >= 0) {
                    // Feasible: Apply the payment
                    System.out.println("  Paying Order " + order.getId() + " (" + order.getValue() + ") fully with " + bestMethodId +
                            " for a discount of " + bestDiscount + ". Effective value: " + effectiveValue);
                    paymentProcessor.processPayment(order, bestMethodId, effectiveValue);
                    fullyPaidInStep1.add(order);
                    paidOrders.add(order); // Mark as paid
                } else {
                    // Not feasible due to limit
                    System.out.println("  Could not pay Order " + order.getId() + " fully with " + bestMethodId + ": Insufficient limit.");
                }
            }
        }
        // Remove orders paid in this step from unpaidOrders list
        unpaidOrders.removeAll(fullyPaidInStep1);
        System.out.println("  Completed Step 1. Remaining unpaid orders: " + unpaidOrders.size());


        // Step 2: Handle Remaining Unpaid Orders with Points Discounts
        System.out.println("\n--- Step 2: Handling Remaining Orders with Points ---");
        List<Order> fullyPaidInStep2 = new ArrayList<>();

        // Process the remaining unpaid orders
        for (Order order : unpaidOrders) {
            BigDecimal originalOrderValue = order.getValue();
            BigDecimal potentialPointsDiscountAmount = BigDecimal.ZERO;
            BigDecimal amountToPayWithPoints = BigDecimal.ZERO; // Amount of points we'd *attempt* to use

            PaymentMethod pointsMethod = paymentMethodsMap.get(POINTS_PAYMENT_METHOD_ID);

            // Check if points method is available at all
            if (pointsMethod == null) {
                System.out.println("  Order " + order.getId() + ": POINTS payment method not available. Skipping points strategies.");
                continue; // Move to the next order or Step 3
            }

            // Option 2a: Try Full Points Payment
            // Check if points limit allows paying the full order value
            if (pointsMethod.getRemainingLimit().compareTo(originalOrderValue) >= 0) {
                // Feasible to pay fully with points from a limit perspective
                BigDecimal calculatedDiscount = paymentProcessor.calculatePointsDiscount(order, originalOrderValue);
                if (calculatedDiscount.compareTo(BigDecimal.ZERO) > 0) {
                    // Full points payment offers a discount
                    System.out.println("  Considering Order " + order.getId() + ": Full points payment feasible with discount " + calculatedDiscount);
                    potentialPointsDiscountAmount = calculatedDiscount;
                    amountToPayWithPoints = originalOrderValue;

                    // Apply the full points payment
                    BigDecimal effectiveValue = originalOrderValue.subtract(potentialPointsDiscountAmount);
                    System.out.println("  Paying Order " + order.getId() + " (" + originalOrderValue + ") fully with POINTS. Effective value: " + effectiveValue);
                    paymentProcessor.processPayment(order, POINTS_PAYMENT_METHOD_ID, amountToPayWithPoints);
                    fullyPaidInStep2.add(order);
                }
            }

            // If not paid fully with points in this step, consider partial points
            if (!fullyPaidInStep2.contains(order)) {
                BigDecimal minPartialPointsAmount = originalOrderValue.multiply(MIN_PARTIAL_POINTS_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);

                // Option 2b: Try Partial Points Payment (at least 10%)
                // Check if we can pay at least 10% of the original value with remaining points
                if (pointsMethod.getRemainingLimit().compareTo(minPartialPointsAmount) >= 0) {

                    // Determine how much points to use for the partial payment.
                    // Greedy choice: use just enough (10%) to trigger the discount.
                    BigDecimal amountFor10PercentDiscount = minPartialPointsAmount;

                    // Double-check if remaining points limit is sufficient for THIS specific amount
                    if (pointsMethod.getRemainingLimit().compareTo(amountFor10PercentDiscount) >= 0) {
                        // Potential for 10% points discount exists
                        BigDecimal calculatedDiscount = paymentProcessor.calculatePointsDiscount(order, amountFor10PercentDiscount);

                        if (calculatedDiscount.compareTo(BigDecimal.ZERO) > 0) {
                            System.out.println("  Considering Order " + order.getId() + ": Partial points payment >= 10% feasible with discount " + calculatedDiscount);
                            potentialPointsDiscountAmount = calculatedDiscount;
                            amountToPayWithPoints = amountFor10PercentDiscount;
                            BigDecimal effectiveValue = originalOrderValue.subtract(potentialPointsDiscountAmount);
                            BigDecimal remainingAmountToPay = effectiveValue.subtract(amountToPayWithPoints); // Amount to pay with traditional method

                            // Find a suitable traditional payment method for the remaining amount
                            // Simple heuristic: Find the first one with enough limit.
                            Optional<PaymentMethod> traditionalMethodForPartial = paymentMethodsMap.values().stream()
                                    .filter(pm -> !POINTS_PAYMENT_METHOD_ID.equals(pm.getId()) && pm.getRemainingLimit().compareTo(remainingAmountToPay) >= 0)
                                    .findFirst();

                            if (traditionalMethodForPartial.isPresent()) {
                                String traditionalMethodId = traditionalMethodForPartial.get().getId();
                                System.out.println("  Paying Order " + order.getId() + " (" + originalOrderValue + ") with POINTS (" + amountToPayWithPoints + ") and " + traditionalMethodId + " (" + remainingAmountToPay + "). Effective value: " + effectiveValue);

                                // Apply the payments
                                paymentProcessor.processPayment(order, POINTS_PAYMENT_METHOD_ID, amountToPayWithPoints);
                                paymentProcessor.processPayment(order, traditionalMethodId, remainingAmountToPay);

                                fullyPaidInStep2.add(order);

                            } else {
                                System.out.println("  Order " + order.getId() + ": Could not find a traditional method for remaining " + remainingAmountToPay + " after partial points payment. Cannot apply partial points strategy.");
                                // If we can't pay the remaining, this strategy is not feasible right now.
                                // The order remains in unpaidOrders for Step 3.
                            }
                        }
                    }
                }
            }

            // If the order was fully paid in this step, add it to paidOrders
            fullyPaidInStep2.forEach(paidOrders::add);
        }
        // Remove orders paid in this step from unpaidOrders list
        unpaidOrders.removeAll(fullyPaidInStep2);
        System.out.println("  Completed Step 2. Remaining unpaid orders: " + unpaidOrders.size());


        // Step 3: Pay Remaining Unpaid Orders with Available Traditional Methods (No Discount)
        System.out.println("\n--- Step 3: Paying Remaining Orders with Available Traditional Methods ---");
        List<Order> fullyPaidInStep3 = new ArrayList<>();

        // Process the remaining unpaid orders
        for (Order order : unpaidOrders) {
            BigDecimal amountToPay = order.getValue();
            boolean paidInThisStep = false;

            // Find a suitable traditional payment method for the full amount
            // Simple heuristic: Find the first one with enough limit.
            Optional<PaymentMethod> traditionalMethodForFull = paymentMethodsMap.values().stream()
                    .filter(pm -> !POINTS_PAYMENT_METHOD_ID.equals(pm.getId()) && pm.getRemainingLimit().compareTo(amountToPay) >= 0)
                    .findFirst();

            if (traditionalMethodForFull.isPresent()) {
                String traditionalMethodId = traditionalMethodForFull.get().getId();
                System.out.println("  Paying remaining Order " + order.getId() + " (" + amountToPay + ") fully with " + traditionalMethodId + " (No specific discount).");
                paymentProcessor.processPayment(order, traditionalMethodId, amountToPay);
                fullyPaidInStep3.add(order);
                paidInThisStep = true;
            } else {
                System.err.println("  Could NOT pay remaining Order " + order.getId() + " (" + amountToPay + "). No available traditional method with sufficient limit.");
                // This case indicates failure to pay all orders.
            }

            if (paidInThisStep) {
                paidOrders.add(order); // Mark as paid
            }
        }
        // Remove orders paid in this step from unpaidOrders list
        unpaidOrders.removeAll(fullyPaidInStep3);
        System.out.println("  Completed Step 3. Remaining unpaid orders: " + unpaidOrders.size());

        System.out.println("\n--- Payment Optimization Finished ---");

        // Return the list of orders that remain unpaid
        return unpaidOrders;
    }

    private BigDecimal calculateMaxBankDiscountForOrder(Order order) {
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (order.getPromotions() != null) {
            for (String promoId : order.getPromotions()) {
                if (!POINTS_PAYMENT_METHOD_ID.equals(promoId) && paymentMethodsMap.containsKey(promoId)) {
                    maxDiscount = maxDiscount.max(paymentProcessor.calculateFullTraditionalPaymentDiscount(order, promoId));
                }
            }
        }
        return maxDiscount;
    }
}