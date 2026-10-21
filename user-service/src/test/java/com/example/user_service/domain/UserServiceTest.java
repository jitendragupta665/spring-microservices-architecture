package com.example.user_service.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UNIT test: plain Java, no Spring, no network. Runs in milliseconds.
 * It checks one class's logic in isolation; this is the base of the test pyramid.
 */
class UserServiceTest {

    private final UserService userService = new UserService();

    @Test
    void findsAKnownUser() {
        assertThat(userService.findById(1)).contains(new User(1, "Rahul", 22));
    }

    @Test
    void unknownIdIsEmptyNotNull() {
        assertThat(userService.findById(99)).isEmpty();
    }
}
