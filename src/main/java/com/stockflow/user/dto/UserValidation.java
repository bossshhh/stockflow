package com.stockflow.user.dto;

/** Limits shared by the user request DTOs; they mirror V5__create_users.sql. */
public final class UserValidation {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int FULL_NAME_MAX_LENGTH = 200;

    /** Length beats complexity rules for password strength (NIST SP 800-63B). */
    public static final int PASSWORD_MIN_LENGTH = 12;

    /**
     * BCrypt only uses the first 72 BYTES; longer input would be silently truncated (two different long
     * passwords could then match). Checked again in bytes by the service, because one character can be
     * several bytes in UTF-8.
     */
    public static final int PASSWORD_MAX_BYTES = 72;

    private UserValidation() {
    }
}
