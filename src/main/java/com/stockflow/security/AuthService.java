package com.stockflow.security;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.stockflow.security.dto.LoginRequest;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.RegisterRequest;
import com.stockflow.user.dto.UserResponse;
import com.stockflow.user.dto.UserValidation;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimiter rateLimiter;
    private final UserService userService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService, AuthRateLimiter rateLimiter, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.userService = userService;
    }

    /**
     * Unknown email, wrong password and disabled account all give the same {@link InvalidCredentialsException}.
     * Emails and passwords are never logged. Rate limited before any hashing (see {@link AuthRateLimiter}).
     *
     * @param clientIp the caller's address (from X-Forwarded-For when behind a trusted proxy)
     */
    public AuthSession login(LoginRequest request, String clientIp) {
        AuthRateLimiter.LoginAttempt attempt = rateLimiter.beginLogin(clientIp, UserService.normalizeEmail(request.email()));
        // Over BCrypt's 72-byte limit no stored password can match; reject without hashing.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > UserValidation.PASSWORD_MAX_BYTES) {
            throw new InvalidCredentialsException();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().strip(), request.password()));
        } catch (AuthenticationException ex) {
            log.info("Login failed: {}", ex.getClass().getSimpleName());
            throw new InvalidCredentialsException();
        }
        attempt.succeeded();
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        log.info("Login succeeded userId={}", user.id());
        return new AuthSession(jwtTokenService.issue(user.id(), user.role(), user.tokenVersion()),
                refreshTokenService.issueForNewLogin(user.id()));
    }

    /** Creates a CUSTOMER account, limited per IP against scripted mass sign-ups. */
    public UserResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.registration(clientIp);
        return userService.register(request);
    }

    /** New access token plus the next refresh token, in exchange for the current refresh token. */
    public AuthSession refresh(String refreshToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        return new AuthSession(jwtTokenService.issue(rotated.userId(), rotated.role(), rotated.tokenVersion()),
                rotated.next());
    }

    /** Ends this login session. The access token stays valid until it expires (at most its short TTL). */
    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }
}
