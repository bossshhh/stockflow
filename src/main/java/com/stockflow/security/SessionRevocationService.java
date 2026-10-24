package com.stockflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stockflow.user.CurrentUser;
import com.stockflow.user.UserService;

/**
 * Ends sessions before their tokens expire: bumps the user's token version (every access token stops
 * working on its next use) and revokes all refresh tokens, in one transaction.
 */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final TokenVersionValidator tokenVersionValidator;

    public SessionRevocationService(UserService userService, RefreshTokenService refreshTokenService,
                                    TokenVersionValidator tokenVersionValidator) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.tokenVersionValidator = tokenVersionValidator;
    }

    /** "Sign out everywhere": every session of this user, on every device. */
    @Transactional
    public void signOutEverywhere(long userId) {
        userService.revokeTokens(userId);
        int refreshTokens = refreshTokenService.revokeAllForUser(userId);
        evictAfterCommit(userId);
        log.info("Signed out everywhere userId={} refreshTokensRevoked={}", userId, refreshTokens);
    }

    /** Admin: lock an account out at once. An admin cannot disable their own account (no lockout by mistake). */
    @Transactional
    public void disable(CurrentUser admin, long userId) {
        if (admin.id() == userId) {
            throw new CannotDisableOwnAccountException();
        }
        userService.disable(userId);
        int refreshTokens = refreshTokenService.revokeAllForUser(userId);
        evictAfterCommit(userId);
        log.info("Disabled userId={} by adminId={} refreshTokensRevoked={}", userId, admin.id(), refreshTokens);
    }

    /** Admin: allow the account to log in again. Its old tokens stay revoked. */
    @Transactional
    public void enable(CurrentUser admin, long userId) {
        userService.enable(userId);
        evictAfterCommit(userId);
        log.info("Enabled userId={} by adminId={}", userId, admin.id());
    }

    /**
     * Evicting BEFORE the commit would let a concurrent request reload the old version and cache it again;
     * after the commit, the next lookup is guaranteed to see the change.
     */
    private void evictAfterCommit(long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tokenVersionValidator.evict(userId);
            }
        });
    }
}
