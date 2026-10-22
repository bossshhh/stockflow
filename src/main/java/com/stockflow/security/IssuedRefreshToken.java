package com.stockflow.security;

import java.time.Instant;

/**
 * A freshly issued refresh token. {@code value} is the only copy of the raw token: it goes into the cookie
 * and is never stored or logged.
 */
public record IssuedRefreshToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedRefreshToken[expiresAt=" + expiresAt + ", value=***]";
    }
}
