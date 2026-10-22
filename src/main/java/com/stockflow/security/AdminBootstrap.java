package com.stockflow.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.stockflow.user.UserService;
import com.stockflow.user.dto.UserValidation;

/**
 * Solves "who creates the first admin?" without a password in source code or in a migration: the account
 * comes from ADMIN_EMAIL / ADMIN_PASSWORD at startup, and only if it does not exist yet.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final SecurityProperties properties;
    private final UserService userService;

    public AdminBootstrap(SecurityProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured()) {
            return;
        }
        if (admin.password().length() < UserValidation.PASSWORD_MIN_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least "
                    + UserValidation.PASSWORD_MIN_LENGTH + " characters");
        }
        userService.ensureAdminExists(admin.email(), admin.password());
    }
}
