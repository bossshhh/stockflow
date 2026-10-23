package com.stockflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled: deletes refresh tokens past their expiry (see {@link RefreshTokenService#deleteExpired()}). */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanup(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(initialDelayString = "${stockflow.housekeeping.initial-delay}",
            fixedDelayString = "${stockflow.housekeeping.cleanup-interval}")
    void scheduledCleanup() {
        deleteExpiredRefreshTokens();
    }

    /** @return how many tokens were deleted */
    public int deleteExpiredRefreshTokens() {
        int deleted = refreshTokenService.deleteExpired();
        if (deleted > 0) {
            log.info("Housekeeping deleted {} expired refresh token(s)", deleted);
        }
        return deleted;
    }
}
