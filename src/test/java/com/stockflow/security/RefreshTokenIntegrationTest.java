package com.stockflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;

/** Refresh tokens: cookie attributes, rotation, theft detection, logout, expiry, disabled accounts. */
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String EMAIL = "ada@example.com";

    @BeforeEach
    void account() {
        createCustomer(EMAIL);
    }

    @Test
    void loginSetsAHardenedRefreshCookieAndNeverReturnsItInTheBody() throws Exception {
        MvcResult login = login();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshTokenCookies.COOKIE_NAME + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=604800");
        assertThat(login.getResponse().getContentAsString())
                .doesNotContain(refreshCookie(login.getResponse()).getValue());
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        Cookie cookie = refreshCookie(login().getResponse());

        String storedHash = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(cookie.getValue())
                .isEqualTo(RefreshTokenService.hash(cookie.getValue()));
    }

    @Test
    void refreshRotatesTheCookieAndReturnsAWorkingAccessToken() throws Exception {
        Cookie first = refreshCookie(login().getResponse());

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        Cookie second = refreshCookie(refreshed.getResponse());

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mockMvc.perform(get(AUTH + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeSession() throws Exception {
        Cookie stolen = refreshCookie(login().getResponse());
        Cookie legitimateNext = refreshCookie(refresh(stolen).andExpect(status().isOk()).andReturn().getResponse());

        // The attacker replays the old token: detected as reuse.
        refresh(stolen).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // The whole family is revoked (and that revocation was committed despite the error response),
        // so the legitimate client's newer token stops working too.
        refresh(legitimateNext).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class)).isZero();
    }

    @Test
    void logoutRevokesTheSessionAndDeletesTheCookie() throws Exception {
        Cookie cookie = refreshCookie(login().getResponse());

        mockMvc.perform(post(AUTH + "/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutACookieIsHarmless() throws Exception {
        mockMvc.perform(post(AUTH + "/logout")).andExpect(status().isNoContent());
    }

    @Test
    void missingOrForgedCookieIsRejected() throws Exception {
        mockMvc.perform(post(AUTH + "/refresh")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(new Cookie(RefreshTokenCookies.COOKIE_NAME, "forged-value")).andExpect(status().isUnauthorized());
        refresh(new Cookie(RefreshTokenCookies.COOKIE_NAME, "x".repeat(5_000))).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Cookie cookie = refreshCookie(login().getResponse());

        clock.advance(Duration.ofDays(7));

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotRefresh() throws Exception {
        Cookie cookie = refreshCookie(login().getResponse());
        jdbcTemplate.update("UPDATE users SET enabled = false WHERE email = ?", EMAIL);

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshesWithOneTokenRotateItExactlyOnce() throws Exception {
        Cookie cookie = refreshCookie(login().getResponse());
        int threads = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    startGate.await();
                    return refresh(cookie).andReturn().getResponse().getStatus();
                }));
            }
            startGate.countDown();
            long successes = 0;
            for (Future<Integer> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == 200) {
                    successes++;
                }
            }
            // The row lock serializes them: the first rotates the token, the rest present an already-rotated
            // token, which is treated as reuse and revokes the session.
            assertThat(successes).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class)).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post(AUTH + "/refresh").cookie(cookie));
    }

    private static Cookie refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(RefreshTokenCookies.COOKIE_NAME);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie;
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
