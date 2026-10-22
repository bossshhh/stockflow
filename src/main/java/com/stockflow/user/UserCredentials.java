package com.stockflow.user;

/** What login needs to verify a password. Only used inside the backend; never serialized to clients. */
public record UserCredentials(long id, String email, String passwordHash, Role role, boolean enabled) {

    @Override
    public String toString() {
        return "UserCredentials[id=" + id + ", role=" + role + ", enabled=" + enabled + "]";
    }
}
