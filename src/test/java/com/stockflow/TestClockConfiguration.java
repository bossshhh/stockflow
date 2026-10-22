package com.stockflow;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the system clock in integration tests. @Primary wins over the Clock bean from ClockConfig. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

    public static final Instant START = Instant.parse("2026-01-15T10:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
