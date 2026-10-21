package com.example.order_service.domain;

import com.example.order_service.client.UserClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UNIT test with a MOCK: OrderService depends on UserClient (a network call). A Mockito mock
 * replaces it, so the test decides what "user-service" answers and runs without any network.
 * A fixed Clock makes createdAt predictable.
 */
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final UserClient userClient = mock(UserClient.class);
    private final OrderService orderService =
            new OrderService(userClient, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void placesAnOrderForAnExistingUser() {
        when(userClient.userExists(1)).thenReturn(true);

        Order order = orderService.placeOrder(1, "book");

        assertThat(order).isEqualTo(new Order(1, 1, "book", NOW));
        assertThat(orderService.findById(1)).contains(order);
    }

    @Test
    void idsIncrease() {
        when(userClient.userExists(1)).thenReturn(true);

        assertThat(orderService.placeOrder(1, "a").id()).isEqualTo(1);
        assertThat(orderService.placeOrder(1, "b").id()).isEqualTo(2);
    }

    @Test
    void unknownUserIsRejectedAndNothingIsStored() {
        when(userClient.userExists(99)).thenReturn(false);

        assertThatThrownBy(() -> orderService.placeOrder(99, "book"))
                .isInstanceOf(UnknownUserException.class)
                .hasMessage("user 99 does not exist");
        assertThat(orderService.findById(1)).isEmpty();
    }

    @Test
    void userServiceFailureIsPassedOnNotHidden() {
        when(userClient.userExists(1)).thenThrow(new UserServiceUnavailableException("down", null));

        assertThatThrownBy(() -> orderService.placeOrder(1, "book"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void missingOrderIsEmpty() {
        assertThat(orderService.findById(7)).isEmpty();
    }
}
