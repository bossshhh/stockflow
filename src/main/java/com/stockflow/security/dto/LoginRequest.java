package com.stockflow.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.stockflow.user.dto.UserValidation;

public record LoginRequest(

        @NotBlank
        @Size(max = UserValidation.EMAIL_MAX_LENGTH)
        String email,

        @NotBlank
        @Size(max = UserValidation.PASSWORD_MAX_BYTES)
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
