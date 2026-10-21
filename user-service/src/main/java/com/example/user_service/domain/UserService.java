package com.example.user_service.domain;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Looks users up. The data is a fixed in-memory map for now; the next Phase 3 step moves it to
 * PostgreSQL behind the same method, so the controller and its callers do not change.
 */
@Service
public class UserService {

    private static final Map<Long, User> USERS = Map.of(
            1L, new User(1, "Rahul", 22),
            2L, new User(2, "Priya", 25),
            3L, new User(3, "Amit", 30));

    /** Optional instead of null: the caller must decide what "not found" means (here: 404). */
    public Optional<User> findById(long id) {
        return Optional.ofNullable(USERS.get(id));
    }
}
