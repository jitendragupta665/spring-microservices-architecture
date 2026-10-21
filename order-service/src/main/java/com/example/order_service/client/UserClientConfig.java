package com.example.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the user-service client. RestClient is Spring's modern synchronous HTTP client
 * (the fluent successor of RestTemplate).
 */
@Configuration
class UserClientConfig {

    @Bean
    UserClient userClient(@Value("${clients.user-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000); // max 1 s to open the TCP connection (dead pod IP, blocked port)
        factory.setReadTimeout(2000);    // max 2 s waiting for the answer; never hold a thread forever
        return new UserClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build());
    }
}
