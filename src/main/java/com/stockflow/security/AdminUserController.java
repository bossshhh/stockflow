package com.stockflow.security;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.stockflow.user.CurrentUser;
import com.stockflow.user.UserService;
import com.stockflow.user.dto.CreateUserRequest;
import com.stockflow.user.dto.UserResponse;

/** ADMIN only (enforced in SecurityConfig for /api/v1/admin/**). */
@RestController
@Tag(name = "Admin", description = "Staff account management (ADMIN only)")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final SessionRevocationService sessionRevocationService;

    public AdminUserController(UserService userService, SessionRevocationService sessionRevocationService) {
        this.userService = userService;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Operation(summary = "Create a STAFF or ADMIN account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createStaffAccount(request));
    }

    @Operation(summary = "Disable an account: it can no longer log in, and every token it holds stops working at once")
    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        sessionRevocationService.disable(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enable a disabled account again (its old tokens stay revoked)")
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        sessionRevocationService.enable(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
