package com.stockflow.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.stockflow.user.Role;
import com.stockflow.user.UserCredentials;

/** The user as Spring Security sees it during login. Exists only for the duration of the login request. */
public class AuthenticatedUser implements UserDetails {

    private final UserCredentials credentials;

    public AuthenticatedUser(UserCredentials credentials) {
        this.credentials = credentials;
    }

    public long id() {
        return credentials.id();
    }

    public Role role() {
        return credentials.role();
    }

    public int tokenVersion() {
        return credentials.tokenVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + credentials.role().name()));
    }

    @Override
    public String getPassword() {
        return credentials.passwordHash();
    }

    @Override
    public String getUsername() {
        return credentials.email();
    }

    @Override
    public boolean isEnabled() {
        return credentials.enabled();
    }
}
