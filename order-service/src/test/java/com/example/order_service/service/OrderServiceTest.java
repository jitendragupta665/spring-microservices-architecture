package com.example.order_service.service;

import com.example.order_service.client.UserClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UNIT test with a MOCK: OrderService depends on UserClient (a network call). A Mockito mock
 * replaces it, so the test controls exactly what "user-service" answers and runs without any
 * network. We test OrderService's own logic, not the network.
 */
class OrderServiceTest {

    private final UserClient userClient = mock(UserClient.class);
    private final OrderService orderService = new OrderService(userClient);

    @Test
    void orderMessageContainsTheUserServiceAnswer() {
        when(userClient.getUser()).thenReturn("user-42");

        assertThat(orderService.createOrder()).isEqualTo("Order created for -> user-42");
    }

    @Test
    void userServiceFailureIsNotHidden() {
        // Today the service has no fallback: a network error goes straight up to the caller.
        // This test documents that behaviour; the resilience phase will change it on purpose.
        when(userClient.getUser()).thenThrow(new ResourceAccessException("user-service down"));

        assertThatThrownBy(orderService::createOrder).isInstanceOf(ResourceAccessException.class);
    }
}
