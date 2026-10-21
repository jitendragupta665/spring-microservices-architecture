package com.example.order_service.domain;

import com.example.order_service.client.UserClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business rules for orders: an order can only be placed for a user that exists.
 *
 * Storage is an in-memory map for now (lost on restart, NOT shared between the 2 order-service
 * pods: an order created on pod A is a 404 on pod B). The next Phase 3 step replaces it with
 * PostgreSQL; this class is the only one that will change.
 */
@Service
public class OrderService {

    private final UserClient userClient;
    private final Clock clock;
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();   // safe for parallel requests
    private final AtomicLong nextId = new AtomicLong(1);

    @Autowired   // two constructors: tell Spring which one to use (the other is for tests)
    public OrderService(UserClient userClient) {
        this(userClient, Clock.systemUTC());
    }

    /** Tests pass a fixed clock so createdAt is predictable. */
    OrderService(UserClient userClient, Clock clock) {
        this.userClient = userClient;
        this.clock = clock;
    }

    /**
     * @throws UnknownUserException            user-service answered 404 for this user
     * @throws UserServiceUnavailableException user-service failed or did not answer in time
     */
    public Order placeOrder(long userId, String item) {
        if (!userClient.userExists(userId)) {
            throw new UnknownUserException(userId);
        }
        Order order = new Order(nextId.getAndIncrement(), userId, item, Instant.now(clock));
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> findById(long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
