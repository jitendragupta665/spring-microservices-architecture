package com.example.order_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate = new RestTemplate();

    public String getUser() {
        return restTemplate.getForObject(
                "http://localhost:8080/user",
                String.class
        );
    }
}