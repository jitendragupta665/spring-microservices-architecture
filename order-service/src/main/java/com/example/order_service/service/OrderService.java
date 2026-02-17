package com.example.order_service.service;

import com.example.order_service.client.UserClient;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final UserClient userClient;

    public OrderService(UserClient userClient) {
        this.userClient = userClient;
    }

    public String createOrder() {
        String user = userClient.getUser();
        return "Order created for -> " + user;
    }
}