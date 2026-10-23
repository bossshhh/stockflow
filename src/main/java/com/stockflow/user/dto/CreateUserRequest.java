package com.stockflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.stockflow.user.Role;

/** Admin-only creation of WAREHOUSE, STAFF or ADMIN accounts. Customers register themselves. */
public record CreateUserRequest(

        @NotBlank
        @Email
        @Size(max = UserValidation.EMAIL_MAX_LENGTH)
        String email,

        @NotBlank
        @Size(min = UserValidation.PASSWORD_MIN_LENGTH, max = UserValidation.PASSWORD_MAX_BYTES)
        String password,

        @NotBlank
        @Size(max = UserValidation.FULL_NAME_MAX_LENGTH)
        String fullName,

        @NotNull
        Role role) {

    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", fullName=" + fullName + ", role=" + role + ", password=***]";
    }
}
