package com.stockflow.order;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Order settings bound from {@code stockflow.orders.*}. Validated at startup: a missing value stops the app.
 *
 * @param confirmedCancelWindow how long after confirmation an order may still be cancelled (inclusive)
 */
@Validated
@ConfigurationProperties(prefix = "stockflow.orders")
public record OrderProperties(@NotNull Duration confirmedCancelWindow) {
}
