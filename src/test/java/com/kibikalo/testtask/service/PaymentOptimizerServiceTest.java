package com.kibikalo.testtask.service;

import static org.junit.jupiter.api.Assertions.*;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class PaymentOptimizerServiceTest {

    private List<Order> exampleOrders;
    private Map<String, PaymentMethod> examplePaymentMethods;
    private PaymentProcessor paymentProcessor; // For discount calculations

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @BeforeEach
    void setUp() {
        // --- Setup Example Orders ---
        exampleOrders = new ArrayList<>();
        Order order1 = new Order();
        order1.setId("ORDER1");
        order1.setValue(new BigDecimal("100.00"));
        order1.setPromotions(Arrays.asList("mZysk"));
        exampleOrders.add(order1);

        Order order2 = new Order();
        order2.setId("ORDER2");
        order2.setValue(new BigDecimal("200.00"));
        order2.setPromotions(Arrays.asList("BosBankrut"));
        exampleOrders.add(order2);

        Order order3 = new Order();
        order3.setId("ORDER3");
        order3.setValue(new BigDecimal("150.00"));
        order3.setPromotions(Arrays.asList("mZysk", "BosBankrut"));
        exampleOrders.add(order3);

        Order order4 = new Order();
        order4.setId("ORDER4");
        order4.setValue(new BigDecimal("50.00"));
        order4.setPromotions(new ArrayList<>()); // Empty promotions list
        exampleOrders.add(order4);

        // --- Setup Example Payment Methods ---
        examplePaymentMethods = new HashMap<>();
        PaymentMethod points = new PaymentMethod();
        points.setId("PUNKTY");
        points.setDiscount(15);
        points.setLimit(new BigDecimal("100.00"));
        examplePaymentMethods.put(points.getId(), points);

        PaymentMethod mZysk = new PaymentMethod();
        mZysk.setId("mZysk");
        mZysk.setDiscount(10);
        mZysk.setLimit(new BigDecimal("180.00"));
        examplePaymentMethods.put(mZysk.getId(), mZysk);

        PaymentMethod bosBankrut = new PaymentMethod();
        bosBankrut.setId("BosBankrut");
        bosBankrut.setDiscount(5);
        bosBankrut.setLimit(new BigDecimal("200.00"));
        examplePaymentMethods.put(bosBankrut.getId(), bosBankrut);

        // PaymentProcessor is needed by PaymentOptimizerService for discount calculations
        // We pass a fresh copy of payment methods to PaymentProcessor as it might be stateful
        // if we were using its processPayment method (though here we only use its calculators).
        Map<String, PaymentMethod> paymentMethodsForProcessor = new HashMap<>();
        examplePaymentMethods.forEach((id, pm) -> {
            PaymentMethod copy = new PaymentMethod();
            copy.setId(pm.getId());
            copy.setDiscount(pm.getDiscount());
            copy.setLimit(pm.getLimit());
            paymentMethodsForProcessor.put(id, copy);
        });
        paymentProcessor = new PaymentProcessor(paymentMethodsForProcessor);
    }

    @Test
    @DisplayName("Test with an unsolvable scenario (e.g., insufficient limits)")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testSolveWithUnsolvableScenario() {
        List<Order> unsolvableOrders = new ArrayList<>();
        Order largeOrder = new Order();
        largeOrder.setId("LARGE_ORDER");
        largeOrder.setValue(new BigDecimal("10000.00")); // Exceeds all limits
        largeOrder.setPromotions(new ArrayList<>());
        unsolvableOrders.add(largeOrder);

        Map<String, PaymentMethod> methodsForOptimizer = new HashMap<>();
        examplePaymentMethods.forEach((id, pm) -> {
            PaymentMethod copy = new PaymentMethod();
            copy.setId(pm.getId());
            copy.setDiscount(pm.getDiscount());
            copy.setLimit(pm.getLimit());
            methodsForOptimizer.put(id, copy);
        });


        PaymentOptimizerService optimizer = new PaymentOptimizerService(
                unsolvableOrders,
                methodsForOptimizer,
                paymentProcessor
        );

        Map<String, BigDecimal> finalTotals = optimizer.solve();

        // In an unsolvable case, bestPaymentAssignments would be null, and solve() returns an empty map.
        assertTrue(finalTotals.isEmpty(), "Final totals should be empty for an unsolvable case.");
    }

    // Due to my time constraints I haven't been able to test this class fully
}