package com.stockflow.user.dto;

import java.time.Instant;

import com.stockflow.user.Role;
import com.stockflow.user.User;

/** Never contains the password hash. */
public record UserResponse(Long id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
