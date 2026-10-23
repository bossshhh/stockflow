package com.stockflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;

/**
 * Only EXPIRED refresh tokens are deleted. A revoked token must stay until it expires: if a stolen, already
 * rotated token is replayed, that row is what reveals the theft (and revokes the whole session).
 */
class RefreshTokenCleanupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenCleanup cleanup;

    @Autowired
    private SecurityProperties properties;

    @Test
    void expiredTokensAreDeletedWhileRevokedButUnexpiredOnesAreKept() {
        long userId = createCustomer("ada@example.com");
        Duration ttl = properties.refresh().ttl();
        refreshTokenService.issueForNewLogin(userId);

        clock.advance(ttl.minusHours(1));
        refreshTokenService.issueForNewLogin(userId);
        refreshTokenService.revokeSession(refreshTokenService.issueForNewLogin(userId).value());
        clock.advance(Duration.ofHours(2));

        assertThat(cleanup.deleteExpiredRefreshTokens()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL", Long.class))
                .as("the revoked, unexpired token is kept for theft detection").isOne();
    }
}
