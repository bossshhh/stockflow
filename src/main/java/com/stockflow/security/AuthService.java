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
import com.stockflow.user.dto.UserValidation;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Unknown email, wrong password and disabled account all give the same {@link InvalidCredentialsException}.
     * Emails and passwords are never logged.
     */
    public AuthSession login(LoginRequest request) {
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
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        log.info("Login succeeded userId={}", user.id());
        return new AuthSession(jwtTokenService.issue(user.id(), user.role()),
                refreshTokenService.issueForNewLogin(user.id()));
    }

    /** New access token plus the next refresh token, in exchange for the current refresh token. */
    public AuthSession refresh(String refreshToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        return new AuthSession(jwtTokenService.issue(rotated.userId(), rotated.role()), rotated.next());
    }

    /** Ends this login session. The access token stays valid until it expires (at most its short TTL). */
    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }
}
