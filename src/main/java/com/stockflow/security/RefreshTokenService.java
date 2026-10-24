package com.stockflow.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockflow.user.UserCredentials;
import com.stockflow.user.UserService;

/**
 * Refresh tokens with rotation and reuse detection:
 * <ul>
 *   <li>Login starts a new family and issues its first token.</li>
 *   <li>Each refresh revokes the presented token and issues the next one in the same family.</li>
 *   <li>Presenting an already-revoked token means it was copied (the legitimate client already rotated it):
 *       the whole family is revoked, logging out both the thief and the victim.</li>
 * </ul>
 * Only SHA-256 hashes are stored. Plain SHA-256 (no salt, no BCrypt) is enough here because the tokens are
 * 256-bit random values: there is nothing to guess, unlike human passwords.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;
    /** Base64url of 32 bytes is 43 characters; anything far longer is not one of our tokens. */
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final SecurityProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserService userService,
                               SecurityProperties properties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userService = userService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new family (a new login session) for this user. */
    @Transactional
    public IssuedRefreshToken issueForNewLogin(long userId) {
        return issue(userId, UUID.randomUUID(), clock.instant());
    }

    /**
     * Exchanges a refresh token for the next one. noRollbackFor: when reuse is detected, the family
     * revocation must be COMMITTED even though we answer with an error; a normal rollback would undo it.
     *
     * @throws InvalidRefreshTokenException if the token is missing, unknown, expired, revoked or reused,
     *                                      or the account no longer exists or is disabled
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = findForUpdate(rawToken).orElseThrow(InvalidRefreshTokenException::new);

        if (current.isRevoked()) {
            int revoked = refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected userId={} family={} revoked={}",
                    current.getUserId(), current.getFamilyId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        // The account may have been disabled or removed since login: end the session for good.
        Optional<UserCredentials> user = userService.findCredentialsById(current.getUserId())
                .filter(UserCredentials::enabled);
        if (user.isEmpty()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        IssuedRefreshToken next = issue(user.get().id(), current.getFamilyId(), now);
        return new RotatedRefreshToken(user.get().id(), user.get().role(), user.get().tokenVersion(), next);
    }

    /**
     * Deletes tokens past their expiry: they can never be used again. Revoked-but-unexpired tokens are kept on
     * purpose: if a stolen, already rotated token is replayed, that row is what reveals the theft.
     */
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpiredBefore(clock.instant());
    }

    /** "Sign out everywhere" and account disabling: every refresh token of this user stops working. */
    @Transactional
    public int revokeAllForUser(long userId) {
        return refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    /** Logout: revokes the whole family of this token. Unknown or missing tokens are ignored (idempotent). */
    @Transactional
    public void revokeSession(String rawToken) {
        findForUpdate(rawToken).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private Optional<RefreshToken> findForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
    }

    private IssuedRefreshToken issue(long userId, UUID familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.refresh().ttl());
        refreshTokenRepository.save(new RefreshToken(userId, hash(raw), familyId, expiresAt, now));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // Every Java runtime is required to provide SHA-256.
            throw new IllegalStateException(HASH_ALGORITHM + " is not available", ex);
        }
    }
}
