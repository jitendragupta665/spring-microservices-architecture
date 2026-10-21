package com.example.order_service.api;

/** No order with this id (on this pod: storage is still in memory). ApiErrors maps it to 404. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("order " + id + " does not exist");
    }
}
