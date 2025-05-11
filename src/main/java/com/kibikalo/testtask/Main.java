package com.kibikalo.testtask;

import com.kibikalo.testtask.io.DataLoader;
import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;
import com.kibikalo.testtask.service.PaymentOptimizerService;
import com.kibikalo.testtask.service.PaymentProcessor;
import com.kibikalo.testtask.validation.DataValidator;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;

public class Main {

    private static final int SCALE = 2; // Consistent scale for currency
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP; // Consistent rounding mode

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar payment-optimizer.jar <orders_json_path> <payment_methods_json_path>");
            System.exit(1);
        }

        String ordersFilePath = args[0];
        String paymentMethodsFilePath = args[1];

        DataLoader dataLoader = new DataLoader();
        DataValidator dataValidator = new DataValidator();

        try {
            List<Order> orders = dataLoader.loadOrders(ordersFilePath);
            List<PaymentMethod> paymentMethodsList = dataLoader.loadPaymentMethods(paymentMethodsFilePath);

            dataValidator.validateData(orders, paymentMethodsList);

            Map<String, PaymentMethod> paymentMethodsMap = paymentMethodsList.stream()
                    .collect(Collectors.toMap(PaymentMethod::getId, paymentMethod -> paymentMethod));

            System.out.println("\nPayment methods available for use (with initial limits):");
            paymentMethodsMap.values().forEach(System.out::println);

            PaymentProcessor paymentProcessor = new PaymentProcessor(paymentMethodsMap);

            // --- Use Backtracking Optimizer ---
            PaymentOptimizerService optimizer = new PaymentOptimizerService(orders, paymentMethodsMap, paymentProcessor);
            Map<String, BigDecimal> finalPaymentTotals = optimizer.solve();

            // Format and print finalPaymentTotals
            System.out.println("\n--- Final Payment Totals by Method (Backtracking) ---");
            if (finalPaymentTotals.isEmpty() || finalPaymentTotals.values().stream().allMatch(amount -> amount.compareTo(BigDecimal.ZERO) == 0)) {
                System.out.println("No payments were processed or no solution found.");
            } else {
                finalPaymentTotals.forEach((methodId, amount) -> {
                    if (amount.compareTo(BigDecimal.ZERO) > 0) {
                        System.out.println(methodId + " " + amount.setScale(SCALE, ROUNDING_MODE));
                    }
                });
            }
            System.out.println("------------------------------------");


        } catch (IOException e) {
            System.err.println("Error loading data: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Data validation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}