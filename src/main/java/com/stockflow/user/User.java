package com.stockflow.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An account. Holds only the password HASH; the plain password never reaches this class. */
@Entity
@Table(name = "users")
public class User {

    /** Must match the constraint name in V5__create_users.sql. */
    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    /** Carried by every access token; bumping it revokes them all (see V11). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Role role, Instant now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.enabled = true;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    /** Every access token issued so far stops working on its next use. */
    public void revokeTokens() {
        tokenVersion++;
    }

    /** Locks the account and revokes its tokens at once. */
    public void disable() {
        enabled = false;
        revokeTokens();
    }

    public void enable() {
        enabled = true;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
