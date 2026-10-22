package com.stockflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;

/** The real path end to end: register, log in, and use the issued token. No mocked authentication here. */
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String PASSWORD = "a-long-enough-password";

    @Test
    void registerLoginAndUseTheToken() throws Exception {
        register("ada@example.com", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String token = login("ada@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get(AUTH + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void passwordIsStoredAsBcryptHashNeverPlainText() throws Exception {
        register("ada@example.com", PASSWORD).andExpect(status().isCreated());

        String stored = jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(stored).startsWith("$2a$").doesNotContain(PASSWORD);
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() throws Exception {
        register("Ada@Example.com", PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
        register("ADA@example.COM", PASSWORD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        login("ADA@EXAMPLE.COM", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        register("ada@example.com", PASSWORD).andExpect(status().isCreated());

        String wrongPassword = login("ada@example.com", "not-the-right-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.message"));
    }

    @Test
    void weakOrOverlongPasswordsAreRejected() throws Exception {
        register("ada@example.com", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        // 40 characters but 80 bytes in UTF-8: passes the character limit, fails BCrypt's 72-byte limit.
        register("bob@example.com", "é".repeat(40)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void registrationCannotChooseARole() throws Exception {
        mockMvc.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "eve@example.com", "password": "%s", "fullName": "Eve", "role": "ADMIN"}
                        """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        register("ada@example.com", PASSWORD).andExpect(status().isCreated());
        String token = accessToken(login("ada@example.com", PASSWORD).andReturn().getResponse().getContentAsString());

        // 15 min lifetime + 60 s clock-skew allowance, then one more minute.
        clock.advance(Duration.ofMinutes(17));

        mockMvc.perform(get(AUTH + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Access token is invalid or expired"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        register("ada@example.com", PASSWORD).andExpect(status().isCreated());
        String token = accessToken(login("ada@example.com", PASSWORD).andReturn().getResponse().getContentAsString());
        // Change one character of the signature (the part after the last dot).
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get(AUTH + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s", "fullName": "Test User"}
                """.formatted(email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }

    private static String accessToken(String loginResponse) {
        return JsonPath.read(loginResponse, "$.accessToken");
    }
}
