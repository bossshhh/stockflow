package com.stockflow.security;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.security.dto.LoginRequest;
import com.stockflow.security.dto.TokenResponse;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.RegisterRequest;
import com.stockflow.user.dto.UserResponse;

@RestController
@Tag(name = "Auth", description = "Register, log in, and see who you are")
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

    static final String BASE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @Operation(summary = "Create a CUSTOMER account (password: 12-72 characters)")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @Operation(summary = "Log in. Returns an access token: click 'Authorize' in Swagger and paste it")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "The account the current token belongs to")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getById(CurrentUser.from(jwt).id()));
    }
}
