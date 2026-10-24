package com.stockflow.security;

import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.security.dto.LoginRequest;
import com.stockflow.security.dto.TokenResponse;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.RegisterRequest;
import com.stockflow.user.dto.UserResponse;

@RestController
@Tag(name = "Auth", description = "Register, log in, refresh the session, log out, and see who you are")
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

    static final String BASE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final SessionRevocationService sessionRevocationService;
    private final Clock clock;

    public AuthController(AuthService authService, UserService userService, RefreshTokenCookies refreshTokenCookies,
                          SessionRevocationService sessionRevocationService, Clock clock) {
        this.sessionRevocationService = sessionRevocationService;
        this.authService = authService;
        this.userService = userService;
        this.refreshTokenCookies = refreshTokenCookies;
        this.clock = clock;
    }

    @Operation(summary = "Create a CUSTOMER account (password: 12-72 characters)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Log in. Returns an access token (paste it into 'Authorize' in Swagger) and sets the "
            + "refresh token as an HttpOnly cookie")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return withSessionCookie(authService.login(request, httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "New access token from the refresh token cookie. The cookie is rotated on every use")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false)
            String refreshToken) {
        return withSessionCookie(authService.refresh(refreshToken));
    }

    @Operation(summary = "Sign out everywhere: ends every session of the signed-in user, on every device, at once")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere(@AuthenticationPrincipal Jwt jwt) {
        sessionRevocationService.signOutEverywhere(CurrentUser.from(jwt).id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    @Operation(summary = "Log out: revokes this session's refresh tokens and deletes the cookie")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = RefreshTokenCookies.COOKIE_NAME, required = false)
            String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    @Operation(summary = "The account the current token belongs to")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getById(CurrentUser.from(jwt).id()));
    }

    private ResponseEntity<TokenResponse> withSessionCookie(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshTokenCookies.create(session.refreshToken(), clock.instant()).toString())
                .body(session.accessToken());
    }
}
