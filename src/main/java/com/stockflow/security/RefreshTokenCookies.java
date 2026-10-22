package com.stockflow.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token cookie. Each attribute is a defence:
 * <ul>
 *   <li>HttpOnly: JavaScript (including injected scripts) cannot read it.</li>
 *   <li>Secure: only sent over HTTPS (switched off for plain-http local setups).</li>
 *   <li>SameSite=Strict: the browser never attaches it to requests started by another site (CSRF).</li>
 *   <li>Path=/api/v1/auth: not sent with ordinary API calls, only to the auth endpoints.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookies {

    public static final String COOKIE_NAME = "stockflow_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE_STRICT = "Strict";

    private final SecurityProperties properties;

    public RefreshTokenCookies(SecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(IssuedRefreshToken token, Instant now) {
        Duration maxAge = Duration.between(now, token.expiresAt());
        return base(token.value()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    /** An expired, empty cookie: tells the browser to delete it. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.refresh().cookieSecure())
                .sameSite(SAME_SITE_STRICT)
                .path(COOKIE_PATH);
    }
}
