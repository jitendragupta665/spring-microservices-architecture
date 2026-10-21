package com.example.user_service.api;

/** Thrown when no user has the requested id; ApiErrors turns it into a 404. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long id) {
        super("user " + id + " does not exist");
    }
}
