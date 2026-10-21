package com.example.user_service.service;

import com.example.user_service.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UNIT test: plain Java, no Spring, no network. Runs in milliseconds.
 * It checks one class's logic in isolation; this is the base of the test pyramid.
 */
class UserServiceTest {

    private final UserService userService = new UserService();

    @Test
    void returnsTheDemoUser() {
        User user = userService.getUser();

        assertThat(user.getName()).isEqualTo("Rahul");
        assertThat(user.getAge()).isEqualTo(22);
    }
}
