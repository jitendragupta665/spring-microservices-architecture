package com.example.order_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/orders, e.g. {"userId":1,"item":"book"}.
 * Validation runs before the controller method (because of @Valid there); a violation is a 400.
 * Long (not long) so a missing userId is null and fails @NotNull instead of silently becoming 0.
 */
public record CreateOrderRequest(
        @NotNull @Positive Long userId,
        @NotBlank @Size(max = 100) String item) {
}
