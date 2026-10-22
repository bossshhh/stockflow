package com.stockflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-registration. Always creates a CUSTOMER: the role is not something a client may choose. */
public record RegisterRequest(

        @NotBlank
        @Email
        @Size(max = UserValidation.EMAIL_MAX_LENGTH)
        String email,

        @NotBlank
        @Size(min = UserValidation.PASSWORD_MIN_LENGTH, max = UserValidation.PASSWORD_MAX_BYTES)
        String password,

        @NotBlank
        @Size(max = UserValidation.FULL_NAME_MAX_LENGTH)
        String fullName) {

    /** Keeps the password out of logs and error messages if this record is ever printed. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + ", password=***]";
    }
}
