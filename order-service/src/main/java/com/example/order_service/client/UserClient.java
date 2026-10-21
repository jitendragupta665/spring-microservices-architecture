package com.example.order_service.client;

import com.example.order_service.domain.UserServiceUnavailableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Outbound HTTP client for user-service.
 *
 * Service discovery is Kubernetes DNS (ADR-0006): the base URL is a plain hostname,
 * http://user-service:8080. CoreDNS resolves it to the Service's ClusterIP and kube-proxy
 * forwards each new connection to one ready pod. No registry, no client-side load balancer.
 */
public class UserClient {

    private final RestClient http;

    public UserClient(RestClient http) {
        this.http = http;
    }

    /**
     * true = 2xx, false = 404. Anything else means we do NOT know whether the user exists,
     * so it must not be reported as "no such user": it becomes UserServiceUnavailableException.
     */
    public boolean userExists(long userId) {
        try {
            http.get().uri("/api/v1/users/{id}", userId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {   // catch order matters: NotFound is
            return false;                                 // also a RestClientException
        } catch (RestClientException e) {                 // 5xx, other 4xx, connect/read timeout
            throw new UserServiceUnavailableException("user-service call failed: " + e.getMessage(), e);
        }
    }
}
