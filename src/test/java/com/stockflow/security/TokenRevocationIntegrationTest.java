package com.stockflow.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;

/**
 * Revoking a user's sessions takes effect on the NEXT request, not when the access token expires (up to
 * 15 minutes later). Uses real logins and real tokens: the injected test tokens of other tests would skip
 * the very check this is about.
 */
class TokenRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String ADMIN_USERS = "/api/v1/admin/users";
    private static final String EMAIL = "ada@example.com";

    private long userId;

    @BeforeEach
    void registerCustomer() {
        userId = createCustomer(EMAIL);
    }

    @Test
    void disablingAUserRejectsTheirExistingAccessTokenOnTheNextRequest() throws Exception {
        Session session = login();
        me(session.accessToken()).andExpect(status().isOk());

        mvcAs(admin()).perform(post(ADMIN_USERS + "/" + userId + "/disable")).andExpect(status().isNoContent());

        me(session.accessToken()).andExpect(status().isUnauthorized());
        refresh(session.refreshCookie()).andExpect(status().isUnauthorized());
        loginRequest().andExpect(status().isUnauthorized());
    }

    @Test
    void aReEnabledUserCanLogInAgainButOldTokensStayRevoked() throws Exception {
        Session before = login();
        mvcAs(admin()).perform(post(ADMIN_USERS + "/" + userId + "/disable")).andExpect(status().isNoContent());
        mvcAs(admin()).perform(post(ADMIN_USERS + "/" + userId + "/enable")).andExpect(status().isNoContent());

        me(before.accessToken()).andExpect(status().isUnauthorized());
        me(login().accessToken()).andExpect(status().isOk());
    }

    @Test
    void signingOutEverywhereEndsEverySessionOfThatUserOnly() throws Exception {
        Session laptop = login();
        Session phone = login();
        long otherId = createCustomer("bob@example.com");
        Session bob = login("bob@example.com");

        mockMvc.perform(post(AUTH + "/logout-all").header(HttpHeaders.AUTHORIZATION, bearer(laptop.accessToken())))
                .andExpect(status().isNoContent());

        me(laptop.accessToken()).andExpect(status().isUnauthorized());
        me(phone.accessToken()).andExpect(status().isUnauthorized());
        refresh(laptop.refreshCookie()).andExpect(status().isUnauthorized());
        refresh(phone.refreshCookie()).andExpect(status().isUnauthorized());
        me(bob.accessToken()).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(otherId));

        me(login().accessToken()).andExpect(status().isOk());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        mvcAs(admin()).perform(post(ADMIN_USERS + "/" + ADMIN_ID + "/disable"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_OWN_ACCOUNT"));
    }

    @Test
    void disablingAnUnknownUserIs404AndOnlyAdminsMayDoIt() throws Exception {
        mvcAs(admin()).perform(post(ADMIN_USERS + "/999999/disable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvcAs(staff()).perform(post(ADMIN_USERS + "/" + userId + "/disable")).andExpect(status().isForbidden());
    }

    private record Session(String accessToken, Cookie refreshCookie) {
    }

    private Session login() throws Exception {
        return login(EMAIL);
    }

    private Session login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        return new Session(token, result.getResponse().getCookie(RefreshTokenCookies.COOKIE_NAME));
    }

    private ResultActions loginRequest() throws Exception {
        return mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, TEST_PASSWORD)));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get(AUTH + "/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
    }

    private ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post(AUTH + "/refresh").cookie(refreshCookie));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
