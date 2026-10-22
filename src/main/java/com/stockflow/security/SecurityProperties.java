package com.stockflow.security;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Security settings bound from {@code stockflow.security.*}; invalid values stop the app at startup. */
@Validated
@ConfigurationProperties(prefix = "stockflow.security")
public record SecurityProperties(
        @Valid @NotNull JwtSettings jwt,
        @Valid @NotNull RefreshSettings refresh,
        @Min(4) @Max(31) int bcryptStrength,
        @Valid @NotNull CorsSettings cors,
        BootstrapAdmin bootstrapAdmin) {

    /**
     * @param secret base64 of at least 32 random bytes (HS256 key)
     * @param ttl    access token lifetime; short, because a JWT cannot be revoked before it expires
     */
    public record JwtSettings(@NotBlank String secret, @NotNull Duration ttl, @NotBlank String issuer) {

        @Override
        public String toString() {
            return "JwtSettings[ttl=" + ttl + ", issuer=" + issuer + ", secret=***]";
        }
    }

    /**
     * @param ttl          refresh token lifetime (how long a user stays signed in without re-entering the password)
     * @param cookieSecure send the cookie over HTTPS only; false only for plain-http local setups
     */
    public record RefreshSettings(@NotNull Duration ttl, boolean cookieSecure) {
    }

    /** Exact origins allowed to call the API from a browser. Never "*". */
    public record CorsSettings(@NotEmpty List<String> allowedOrigins) {
    }

    /** Optional first ADMIN account; ignored when the email or password is blank. */
    public record BootstrapAdmin(String email, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "BootstrapAdmin[email=" + email + ", password=***]";
        }
    }
}
