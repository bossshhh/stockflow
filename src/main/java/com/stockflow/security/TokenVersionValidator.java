package com.stockflow.security;

import java.util.Optional;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stockflow.user.UserService;

/**
 * Rejects access tokens that were revoked before they expired: the token's version ("tv" claim) must equal
 * the user's current token version, and the account must still be enabled. Runs inside the JWT decoder, so
 * it applies to every request without any controller code.
 *
 * <p>The lookup is cached briefly so most requests do not touch the database. This instance evicts a user's
 * entry right after a revocation commits ({@link SessionRevocationService}), so here the effect is
 * immediate; other instances notice within the cache TTL. Tokens issued before versions existed have no
 * claim and count as version 0.
 */
@Component
public class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token has been revoked", null);
    private static final long MAX_CACHED_USERS = 100_000;

    private record TokenState(int version, boolean enabled) {
    }

    private final UserService userService;
    private final Cache<Long, Optional<TokenState>> current;

    public TokenVersionValidator(UserService userService, SecurityProperties properties) {
        this.userService = userService;
        this.current = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_USERS)
                .expireAfterWrite(properties.jwt().revocationCacheTtl())
                .build();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        Number claim = jwt.getClaim(JwtTokenService.TOKEN_VERSION_CLAIM);
        int tokenVersion = claim == null ? 0 : claim.intValue();
        Optional<TokenState> state = current.get(userId, id -> userService.findCredentialsById(id)
                .map(user -> new TokenState(user.tokenVersion(), user.enabled())));
        boolean valid = state.isPresent() && state.get().enabled() && state.get().version() == tokenVersion;
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }

    /** Forget the cached state, so the next request sees the database. Call after the change commits. */
    void evict(long userId) {
        current.invalidate(userId);
    }
}
