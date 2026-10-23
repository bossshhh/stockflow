package com.stockflow.order;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Order settings bound from {@code stockflow.orders.*}. Validated at startup: a missing value stops the app.
 *
 * @param confirmedCancelWindow how long after confirmation an order may still be cancelled (inclusive)
 * @param idempotencyKeyTtl     how long an Idempotency-Key returns its order (inclusive); after that it is free again
 */
@Validated
@ConfigurationProperties(prefix = "stockflow.orders")
public record OrderProperties(@NotNull Duration confirmedCancelWindow, @NotNull Duration idempotencyKeyTtl) {
}
