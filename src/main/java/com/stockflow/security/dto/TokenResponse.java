package com.stockflow.security.dto;

/**
 * @param tokenType always "Bearer": send it as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn seconds until the token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", accessToken=***]";
    }
}
