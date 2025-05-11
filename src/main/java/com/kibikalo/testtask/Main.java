package com.kibikalo.testtask;

import com.kibikalo.testtask.io.DataLoader;
import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;
import com.kibikalo.testtask.service.PaymentProcessor;
import com.kibikalo.testtask.validation.DataValidator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
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

            System.out.println("Successfully loaded orders:");
            orders.forEach(System.out::println);

            System.out.println("\nSuccessfully loaded payment methods:");
            paymentMethodsList.forEach(System.out::println);

            Map<String, PaymentMethod> paymentMethodsMap = paymentMethodsList.stream()
                    .collect(Collectors.toMap(PaymentMethod::getId, paymentMethod -> paymentMethod));

            System.out.println("\nPayment methods available for use (with initial limits):");
            paymentMethodsMap.values().forEach(System.out::println);


            PaymentProcessor paymentProcessor = new PaymentProcessor(paymentMethodsMap);

        } catch (IOException e) {
            System.err.println("Error loading data: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Data validation error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}