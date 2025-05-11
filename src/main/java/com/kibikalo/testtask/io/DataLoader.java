package com.kibikalo.testtask.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kibikalo.testtask.model.Order;
import com.kibikalo.testtask.model.PaymentMethod;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DataLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Order> loadOrders(String filePath) throws IOException {
        File file = new File(filePath);
        return objectMapper.readValue(file, new TypeReference<List<Order>>() {});
    }

    public List<PaymentMethod> loadPaymentMethods(String filePath) throws IOException {
        File file = new File(filePath);
        return objectMapper.readValue(file, new TypeReference<List<PaymentMethod>>() {});
    }
}