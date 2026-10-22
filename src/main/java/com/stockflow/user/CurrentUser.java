package com.stockflow.user;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is making the request, taken from the validated JWT. Services receive this instead of reading the
 * security context themselves, so they stay easy to unit test.
 */
public record CurrentUser(long id, Role role) {

    /** JWT claim holding the user's roles; also read by the security config to build authorities. */
    public static final String ROLES_CLAIM = "roles";

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null || roles.size() != 1) {
            throw new IllegalStateException("Token must carry exactly one role");
        }
        return new CurrentUser(Long.parseLong(jwt.getSubject()), Role.valueOf(roles.getFirst()));
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }
}
