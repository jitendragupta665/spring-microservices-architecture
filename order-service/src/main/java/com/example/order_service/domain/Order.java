package com.example.order_service.domain;

import java.time.Instant;

/** A placed order. Immutable record; serialized to JSON as-is by the API. */
public record Order(long id, long userId, String item, Instant createdAt) {
}
