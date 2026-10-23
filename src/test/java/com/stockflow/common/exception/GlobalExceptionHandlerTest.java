package com.stockflow.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

/** Bug 6 from the QA session: with the database down, clients got 500 instead of a retryable 503. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void databaseUnreachableIs503WithoutInternalDetails() {
        // What JpaTransactionManager throws when Hikari cannot hand out a connection.
        CannotCreateTransactionException ex = new CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction",
                new IllegalStateException("HikariPool-1 - Connection is not available"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/catalog");

        ResponseEntity<ApiError> response = handler.handleDatabaseUnavailable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("Hikari", "JPA");
    }
}
