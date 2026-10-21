package com.example.order_service.domain;

/**
 * The order names a user that user-service says does not exist. The request is well-formed
 * but cannot be processed: the API answers 422, not 404 (the order endpoint itself exists).
 */
public class UnknownUserException extends RuntimeException {

    public UnknownUserException(long userId) {
        super("user " + userId + " does not exist");
    }
}
