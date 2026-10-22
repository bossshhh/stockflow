package com.stockflow.security;

import com.stockflow.security.dto.TokenResponse;

/**
 * Result of login or refresh: the access token goes in the response body, the refresh token in an
 * HttpOnly cookie (never in the body, so page scripts never see it).
 */
public record AuthSession(TokenResponse accessToken, IssuedRefreshToken refreshToken) {
}
