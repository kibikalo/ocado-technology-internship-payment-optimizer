package com.kibikalo.testtask.service;

import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentProcessorTest {

    private Map<String, PaymentMethod> paymentMethodsMap;
    private PaymentProcessor paymentProcessor;

    private static final BigDecimal EXPECTED_ZERO_DISCOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @BeforeEach
    void setUp() {
        // Set up payment methods before each test
        PaymentMethod points = new PaymentMethod();
        points.setId("PUNKTY");
        points.setDiscount(15);
        points.setLimit(new BigDecimal("100.00"));

        PaymentMethod mZysk = new PaymentMethod();
        mZysk.setId("mZysk");
        mZysk.setDiscount(10);
        mZysk.setLimit(new BigDecimal("180.00"));

        PaymentMethod bosBankrut = new PaymentMethod();
        bosBankrut.setId("BosBankrut");
        bosBankrut.setDiscount(5);
        bosBankrut.setLimit(new BigDecimal("200.00"));

        paymentMethodsMap = new HashMap<>();
        paymentMethodsMap.put(points.getId(), points);
        paymentMethodsMap.put(mZysk.getId(), mZysk);
        paymentMethodsMap.put(bosBankrut.getId(), bosBankrut);

        // Initialize PaymentProcessor with the payment methods
        paymentProcessor = new PaymentProcessor(new HashMap<>(paymentMethodsMap));
    }

    @Test
    @DisplayName("Calculate full traditional payment discount when promotion is applicable")
    void testCalculateFullTraditionalPaymentDiscount_Applicable() {
        Order order = new Order();
        order.setId("ORDER1");
        order.setValue(new BigDecimal("100.00"));
        order.setPromotions(Arrays.asList("mZysk", "SomeOtherPromo"));

        // Expected discount: 10% of 100.00 = 10.00
        BigDecimal expectedDiscount = new BigDecimal("10.00");
        BigDecimal actualDiscount = paymentProcessor.calculateFullTraditionalPaymentDiscount(order, "mZysk");

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 10% for applicable mZysk promo");
    }

    @Test
    @DisplayName("Calculate full traditional payment discount when promotion is NOT applicable")
    void testCalculateFullTraditionalPaymentDiscount_NotApplicable() {
        Order order = new Order();
        order.setId("ORDER2");
        order.setValue(new BigDecimal("200.00"));
        order.setPromotions(Arrays.asList("BosBankrut")); // mZysk is not in promotions

        // Expected discount: 0 (mZysk promo not applicable)
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = paymentProcessor.calculateFullTraditionalPaymentDiscount(order, "mZysk");

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 when mZysk promo is not applicable");
    }

    @Test
    @DisplayName("Calculate full traditional payment discount when order has no promotions list")
    void testCalculateFullTraditionalPaymentDiscount_NoPromotionsList() {
        Order order = new Order();
        order.setId("ORDER3");
        order.setValue(new BigDecimal("150.00"));
        // promotions list is null or empty

        // Expected discount: 0 (no applicable bank card promo)
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = paymentProcessor.calculateFullTraditionalPaymentDiscount(order, "BosBankrut");

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 when order has no promotions list");
    }

    @Test
    @DisplayName("Calculate full traditional payment discount for a non-existent method")
    void testCalculateFullTraditionalPaymentDiscount_UnknownMethod() {
        Order order = new Order();
        order.setId("ORDER4");
        order.setValue(new BigDecimal("50.00"));
        order.setPromotions(Arrays.asList("FakeBank"));

        // Expected discount: 0 (method 'FakeBank' doesn't exist)
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = paymentProcessor.calculateFullTraditionalPaymentDiscount(order, "FakeBank");

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 for a non-existent payment method");
    }


    @Test
    @DisplayName("Calculate points discount for full points payment")
    void testCalculatePointsDiscount_FullPointsPayment() {
        Order order = new Order();
        order.setId("ORDER5");
        order.setValue(new BigDecimal("100.00"));
        order.setPromotions(Arrays.asList("mZysk")); // Promotions shouldn't affect full points discount

        // Expected discount (PUNKTY discount is 15%): 15% of 100.00 = 15.00
        BigDecimal expectedDiscount = new BigDecimal("15.00");
        BigDecimal amountPaidWithPoints = new BigDecimal("100.00"); // Full amount
        BigDecimal actualDiscount = paymentProcessor.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be PUNKTY discount for full points payment");
    }

    @Test
    @DisplayName("Calculate points discount for partial points payment (>= 10%)")
    void testCalculatePointsDiscount_PartialPointsPayment_Above10Percent() {
        Order order = new Order();
        order.setId("ORDER6");
        order.setValue(new BigDecimal("200.00"));
        order.setPromotions(Arrays.asList("BosBankrut")); // Promotions shouldn't affect partial points points discount

        // Pay 25.00 with points (which is 12.5% of 200.00) - should get 10% discount on full order
        BigDecimal amountPaidWithPoints = new BigDecimal("25.00");
        // Expected discount: 10% of 200.00 = 20.00
        BigDecimal expectedDiscount = new BigDecimal("20.00");
        BigDecimal actualDiscount = paymentProcessor.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 10% for partial points payment >= 10%");
    }

    @Test
    @DisplayName("Calculate points discount for partial points payment (exactly 10%)")
    void testCalculatePointsDiscount_PartialPointsPayment_Exactly10Percent() {
        Order order = new Order();
        order.setId("ORDER7");
        order.setValue(new BigDecimal("150.00"));

        // Pay 15.00 with points (exactly 10% of 150.00) - should get 10% discount on full order
        BigDecimal amountPaidWithPoints = new BigDecimal("15.00");
        // Expected discount: 10% of 150.00 = 15.00
        BigDecimal expectedDiscount = new BigDecimal("15.00");
        BigDecimal actualDiscount = paymentProcessor.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 10% for partial points payment exactly 10%");
    }


    @Test
    @DisplayName("Calculate points discount for less than 10% points payment")
    void testCalculatePointsDiscount_LessThan10Percent() {
        Order order = new Order();
        order.setId("ORDER8");
        order.setValue(new BigDecimal("50.00"));

        // Pay 4.99 with points (less than 10% of 50.00)
        BigDecimal amountPaidWithPoints = new BigDecimal("4.99");
        // Expected discount: 0
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = paymentProcessor.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 for points payment < 10%");
    }

    @Test
    @DisplayName("Calculate points discount for zero points payment")
    void testCalculatePointsDiscount_ZeroPointsPayment() {
        Order order = new Order();
        order.setId("ORDER9");
        order.setValue(new BigDecimal("100.00"));

        // Pay 0 with points
        BigDecimal amountPaidWithPoints = BigDecimal.ZERO;
        // Expected discount: 0
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = paymentProcessor.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 for zero points payment");
    }

    @Test
    @DisplayName("Calculate points discount for full points payment when PUNKTY method not defined (defensive check)")
    void testCalculatePointsDiscount_FullPointsPayment_NoPointsMethod() {
        // Create a PaymentProcessor WITHOUT the PUNKTY method
        Map<String, PaymentMethod> methodsWithoutPoints = new HashMap<>();
        PaymentMethod mZysk = new PaymentMethod();
        mZysk.setId("mZysk");
        mZysk.setDiscount(10);
        mZysk.setLimit(new BigDecimal("180.00"));
        methodsWithoutPoints.put(mZysk.getId(), mZysk);
        PaymentProcessor processorWithoutPoints = new PaymentProcessor(methodsWithoutPoints);


        Order order = new Order();
        order.setId("ORDER10");
        order.setValue(new BigDecimal("100.00"));

        // Pay 100.00 with points
        BigDecimal amountPaidWithPoints = new BigDecimal("100.00");
        // Expected discount: 0 (since PUNKTY method is missing)
        BigDecimal expectedDiscount = EXPECTED_ZERO_DISCOUNT;
        BigDecimal actualDiscount = processorWithoutPoints.calculatePointsDiscount(order, amountPaidWithPoints);

        assertEquals(0, expectedDiscount.compareTo(actualDiscount), "Discount should be 0 if PUNKTY method is missing for full points payment");
    }

    // Add tests for calculateEffectiveOrderValue later when implemented
}