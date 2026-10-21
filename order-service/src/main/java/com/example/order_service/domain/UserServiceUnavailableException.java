package com.example.order_service.domain;

/**
 * user-service could not give an answer: an error status (5xx, unexpected 4xx), a refused
 * connection, or a timeout. Not the caller's fault, and possibly temporary: the API answers 503.
 */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
