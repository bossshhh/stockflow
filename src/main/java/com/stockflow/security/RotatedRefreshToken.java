package com.stockflow.security;

import com.stockflow.user.Role;

/** Result of a successful refresh: who the user is (for the new access token) and the next refresh token. */
public record RotatedRefreshToken(long userId, Role role, int tokenVersion, IssuedRefreshToken next) {
}
