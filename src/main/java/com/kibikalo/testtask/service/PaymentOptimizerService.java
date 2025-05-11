package com.kibikalo.testtask.service;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class PaymentOptimizerService {

    private final List<Order> orders;
    private final Map<String, PaymentMethod> initialPaymentMethods; // Original limits
    private final PaymentProcessor paymentProcessor; // For discount calculations

    private BigDecimal maxTotalDiscountFound = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private List<PaymentAssignment> bestPaymentAssignments = null;
    private BigDecimal maxPointsUsedWithMaxDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);


    // Constants
    private static final String POINTS_PAYMENT_METHOD_ID = "PUNKTY";
    private static final BigDecimal MIN_PARTIAL_POINTS_PERCENTAGE = new BigDecimal("0.10");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    // Helper class to store how an order was paid
    private static class PaymentAssignment {
        String orderId;
        String traditionalMethodId; // null if only points
        BigDecimal amountPaidWithPoints;
        BigDecimal amountPaidWithTraditional;
        BigDecimal discountApplied;

        PaymentAssignment(String orderId, String traditionalMethodId, BigDecimal amountPaidWithPoints, BigDecimal amountPaidWithTraditional, BigDecimal discountApplied) {
            this.orderId = orderId;
            this.traditionalMethodId = traditionalMethodId;
            this.amountPaidWithPoints = amountPaidWithPoints.setScale(SCALE, ROUNDING_MODE);
            this.amountPaidWithTraditional = amountPaidWithTraditional.setScale(SCALE, ROUNDING_MODE);
            this.discountApplied = discountApplied.setScale(SCALE, ROUNDING_MODE);
        }

        @Override
        public String toString() {
            return "Order: " + orderId +
                    (traditionalMethodId != null ? ", Traditional: " + traditionalMethodId + " (" + amountPaidWithTraditional + ")" : "") +
                    (amountPaidWithPoints.compareTo(BigDecimal.ZERO) > 0 ? ", Points: " + amountPaidWithPoints : "") +
                    ", Discount: " + discountApplied;
        }
    }


    public PaymentOptimizerService(List<Order> orders, Map<String, PaymentMethod> paymentMethods, PaymentProcessor paymentProcessor) {
        // Sort orders initially - might help prune branches or find good solutions faster
        this.orders = new ArrayList<>(orders);
        this.orders.sort(Comparator.comparing(Order::getValue).reversed());

        this.initialPaymentMethods = new HashMap<>();
        // Create deep copies of payment methods to manage their state independently
        for (Map.Entry<String, PaymentMethod> entry : paymentMethods.entrySet()) {
            PaymentMethod original = entry.getValue();
            PaymentMethod copy = new PaymentMethod();
            copy.setId(original.getId());
            copy.setDiscount(original.getDiscount());
            copy.setLimit(original.getLimit()); // This also sets remainingLimit
            this.initialPaymentMethods.put(entry.getKey(), copy);
        }
        this.paymentProcessor = paymentProcessor; // Used for discount calculations
    }

    public Map<String, BigDecimal> solve() {
        System.out.println("\n--- Starting Backtracking Payment Optimization ---");
        // Create a mutable copy of payment methods for the recursive calls
        Map<String, PaymentMethod> currentPaymentMethodsState = new HashMap<>();
        for (Map.Entry<String, PaymentMethod> entry : initialPaymentMethods.entrySet()) {
            PaymentMethod original = entry.getValue();
            PaymentMethod copy = new PaymentMethod();
            copy.setId(original.getId());
            copy.setDiscount(original.getDiscount());
            copy.setLimit(original.getLimit()); // This also sets remainingLimit
            currentPaymentMethodsState.put(entry.getKey(), copy);
        }

        backtrack(0, currentPaymentMethodsState, new ArrayList<>(), BigDecimal.ZERO, BigDecimal.ZERO);

        if (bestPaymentAssignments == null) {
            System.err.println("Backtracking: No solution found to pay all orders.");
            return new HashMap<>();
        }

        System.out.println("\nBacktracking: Best solution found with total discount: " + maxTotalDiscountFound);
        System.out.println("Points used in best solution: " + maxPointsUsedWithMaxDiscount);
        bestPaymentAssignments.forEach(System.out::println);

        // Reconstruct the final payment totals map from bestPaymentAssignments
        Map<String, BigDecimal> finalTotals = new HashMap<>();
        initialPaymentMethods.keySet().forEach(id -> finalTotals.put(id, BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE)));

        for (PaymentAssignment assignment : bestPaymentAssignments) {
            if (assignment.amountPaidWithPoints.compareTo(BigDecimal.ZERO) > 0) {
                finalTotals.merge(POINTS_PAYMENT_METHOD_ID, assignment.amountPaidWithPoints, BigDecimal::add);
            }
            if (assignment.traditionalMethodId != null && assignment.amountPaidWithTraditional.compareTo(BigDecimal.ZERO) > 0) {
                finalTotals.merge(assignment.traditionalMethodId, assignment.amountPaidWithTraditional, BigDecimal::add);
            }
        }
        return finalTotals;
    }

    private void backtrack(int orderIndex,
                           Map<String, PaymentMethod> currentMethodsState,
                           List<PaymentAssignment> currentAssignments,
                           BigDecimal currentTotalDiscount,
                           BigDecimal currentTotalPointsUsed) {

        // Base Case: All orders processed
        if (orderIndex == orders.size()) {
            if (currentTotalDiscount.compareTo(maxTotalDiscountFound) > 0) {
                maxTotalDiscountFound = currentTotalDiscount;
                bestPaymentAssignments = new ArrayList<>(currentAssignments);
                maxPointsUsedWithMaxDiscount = currentTotalPointsUsed;
                System.out.println("  New best solution: Discount " + maxTotalDiscountFound + ", Points " + maxPointsUsedWithMaxDiscount);
            } else if (currentTotalDiscount.compareTo(maxTotalDiscountFound) == 0) {
                // If discounts are equal, prefer the one using more points
                if (currentTotalPointsUsed.compareTo(maxPointsUsedWithMaxDiscount) > 0) {
                    maxTotalDiscountFound = currentTotalDiscount; // Redundant but clear
                    bestPaymentAssignments = new ArrayList<>(currentAssignments);
                    maxPointsUsedWithMaxDiscount = currentTotalPointsUsed;
                    System.out.println("  New best solution (same discount, more points): Discount " + maxTotalDiscountFound + ", Points " + maxPointsUsedWithMaxDiscount);
                }
            }
            return;
        }

        // Pruning: If current best discount + max possible remaining discount < maxTotalDiscountFound, prune.

        Order currentOrder = orders.get(orderIndex);
        boolean orderPaidInThisPath = false;

        // --- Try all payment options for currentOrder ---

        // Option 1: Full Traditional Payment with Promo
        if (currentOrder.getPromotions() != null) {
            for (String promoId : currentOrder.getPromotions()) {
                if (POINTS_PAYMENT_METHOD_ID.equals(promoId) || !currentMethodsState.containsKey(promoId)) continue;

                PaymentMethod traditionalMethod = currentMethodsState.get(promoId);
                BigDecimal discount = paymentProcessor.calculateFullTraditionalPaymentDiscount(currentOrder, promoId);
                BigDecimal effectiveValue = currentOrder.getValue().subtract(discount);

                if (discount.compareTo(BigDecimal.ZERO) >= 0 && // Allow zero discount if it's the only way
                        traditionalMethod.getRemainingLimit().compareTo(effectiveValue) >= 0) {

                    traditionalMethod.useAmount(effectiveValue);
                    currentAssignments.add(new PaymentAssignment(currentOrder.getId(), promoId, BigDecimal.ZERO, effectiveValue, discount));

                    backtrack(orderIndex + 1, currentMethodsState, currentAssignments,
                            currentTotalDiscount.add(discount), currentTotalPointsUsed);

                    currentAssignments.remove(currentAssignments.size() - 1);
                    traditionalMethod.addAmount(effectiveValue); // Backtrack
                    orderPaidInThisPath = true;
                }
            }
        }

        // Option 2: Full Points Payment
        PaymentMethod pointsMethod = currentMethodsState.get(POINTS_PAYMENT_METHOD_ID);
        if (pointsMethod != null) {
            BigDecimal discount = paymentProcessor.calculatePointsDiscount(currentOrder, currentOrder.getValue());
            BigDecimal effectiveValue = currentOrder.getValue().subtract(discount); // Effective value is what we pay, but points used is full order value

            if (discount.compareTo(BigDecimal.ZERO) >= 0 &&
                    pointsMethod.getRemainingLimit().compareTo(currentOrder.getValue()) >= 0) { // Use full order value from points

                pointsMethod.useAmount(currentOrder.getValue());
                currentAssignments.add(new PaymentAssignment(currentOrder.getId(), null, currentOrder.getValue(), BigDecimal.ZERO, discount));

                backtrack(orderIndex + 1, currentMethodsState, currentAssignments,
                        currentTotalDiscount.add(discount), currentTotalPointsUsed.add(currentOrder.getValue()));

                currentAssignments.remove(currentAssignments.size() - 1);
                pointsMethod.addAmount(currentOrder.getValue()); // Backtrack
                orderPaidInThisPath = true;
            }
        }

        // Option 3: Partial Points Payment (>= 10%) + Traditional
        if (pointsMethod != null) {
            BigDecimal minPartialPointsToUse = currentOrder.getValue().multiply(MIN_PARTIAL_POINTS_PERCENTAGE).setScale(SCALE, ROUNDING_MODE);
            // Try using exactly minPartialPointsToUse, or more if it doesn't change the 10% discount rule
            // For simplicity, let's stick to minPartialPointsToUse to trigger the 10% discount.

            if (pointsMethod.getRemainingLimit().compareTo(minPartialPointsToUse) >= 0) {
                BigDecimal discount = paymentProcessor.calculatePointsDiscount(currentOrder, minPartialPointsToUse);

                if (discount.compareTo(BigDecimal.ZERO) > 0) { // Ensure partial points actually give a discount
                    BigDecimal effectiveOrderValueAfterPointsDiscount = currentOrder.getValue().subtract(discount);
                    BigDecimal remainingToPayWithTraditional = effectiveOrderValueAfterPointsDiscount.subtract(minPartialPointsToUse);

                    for (Map.Entry<String, PaymentMethod> entry : currentMethodsState.entrySet()) {
                        String traditionalMethodId = entry.getKey();
                        PaymentMethod traditionalMethod = entry.getValue();

                        if (POINTS_PAYMENT_METHOD_ID.equals(traditionalMethodId)) continue;

                        if (traditionalMethod.getRemainingLimit().compareTo(remainingToPayWithTraditional) >= 0) {
                            pointsMethod.useAmount(minPartialPointsToUse);
                            traditionalMethod.useAmount(remainingToPayWithTraditional);
                            currentAssignments.add(new PaymentAssignment(currentOrder.getId(), traditionalMethodId, minPartialPointsToUse, remainingToPayWithTraditional, discount));

                            backtrack(orderIndex + 1, currentMethodsState, currentAssignments,
                                    currentTotalDiscount.add(discount), currentTotalPointsUsed.add(minPartialPointsToUse));

                            currentAssignments.remove(currentAssignments.size() - 1);
                            traditionalMethod.addAmount(remainingToPayWithTraditional);
                            pointsMethod.addAmount(minPartialPointsToUse); // Backtrack
                            orderPaidInThisPath = true;
                        }
                    }
                }
            }
        }

        // Option 4: Full Traditional Payment (No Promo / No Discount from this method)
        // This is a fallback if other options with discounts are not better or not feasible.
        // We should only try this if no discount strategy was better.
        // The logic for "best" is handled by the maxTotalDiscountFound comparison.
        for (Map.Entry<String, PaymentMethod> entry : currentMethodsState.entrySet()) {
            String traditionalMethodId = entry.getKey();
            PaymentMethod traditionalMethod = entry.getValue();

            if (POINTS_PAYMENT_METHOD_ID.equals(traditionalMethodId)) continue;

            // Check if this method is NOT in the order's promotions, or if it is, the discount is zero
            boolean noPromoDiscount = true;
            if (currentOrder.getPromotions() != null && currentOrder.getPromotions().contains(traditionalMethodId)) {
                if (paymentProcessor.calculateFullTraditionalPaymentDiscount(currentOrder, traditionalMethodId).compareTo(BigDecimal.ZERO) > 0) {
                    noPromoDiscount = false; // This path is covered by Option 1
                }
            }

            if (noPromoDiscount && traditionalMethod.getRemainingLimit().compareTo(currentOrder.getValue()) >= 0) {
                BigDecimal discount = BigDecimal.ZERO; // No discount for this specific path

                traditionalMethod.useAmount(currentOrder.getValue());
                currentAssignments.add(new PaymentAssignment(currentOrder.getId(), traditionalMethodId, BigDecimal.ZERO, currentOrder.getValue(), discount));

                backtrack(orderIndex + 1, currentMethodsState, currentAssignments,
                        currentTotalDiscount.add(discount), currentTotalPointsUsed);

                currentAssignments.remove(currentAssignments.size() - 1);
                traditionalMethod.addAmount(currentOrder.getValue()); // Backtrack
                orderPaidInThisPath = true;
            }
        }
        // If !orderPaidInThisPath after trying all options, it means this order couldn't be paid with current state,
        // so this branch of recursion is invalid. The base case (all orders processed) won't be hit.
    }
}