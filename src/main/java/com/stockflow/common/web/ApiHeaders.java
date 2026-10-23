package com.stockflow.common.web;

/** Custom HTTP headers of the API, shared by the controllers that use them and the CORS configuration. */
public final class ApiHeaders {

    /** Client-chosen key that makes placing an order safe to retry. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Set to "true" on a response that returns an earlier result for a repeated idempotency key. */
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private ApiHeaders() {
    }
}
