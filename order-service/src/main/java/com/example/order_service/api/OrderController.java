package com.example.order_service.api;

import com.example.order_service.domain.Order;
import com.example.order_service.domain.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST API for orders (ADR-0007).
 *   POST /api/v1/orders       create  -> 201 + Location, 400 invalid body, 422 unknown user, 503 user-service down
 *   GET  /api/v1/orders/{id}  read    -> 200, 404
 * Creating is a POST, never a GET: GET must be safe to repeat (browsers, caches and retries
 * re-send it), and a repeated create would place a second order.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.placeOrder(request.userId(), request.item());
        // Relative URI: valid per RFC 9110 and correct behind the gateway, where this pod's own
        // host name (order-service:8081) means nothing to the client.
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
        return orderService.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
