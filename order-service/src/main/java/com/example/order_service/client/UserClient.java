package com.example.order_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate;

    public UserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getUser() {
        return restTemplate.getForObject(
                "http://USER-SERVICE/user",
                String.class
        );
    }
}