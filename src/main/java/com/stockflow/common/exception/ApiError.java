package com.stockflow.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * The single error body shape for the whole API.
 *
 * @param fieldErrors per-field problems for validation failures; empty for every other error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
