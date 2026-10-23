package com.stockflow.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.stockflow.AbstractIntegrationTest;

/**
 * Small limits so the tests stay short: 3 failed logins per email per 15 min (one back every 5 min),
 * 5 failed logins per IP per 10 min, 2 registrations per IP per hour. Every test uses its own IP and
 * emails, so the in-memory buckets of different tests never meet.
 */
@TestPropertySource(properties = {
        "stockflow.security.rate-limit.failed-logins-per-email.attempts=3",
        "stockflow.security.rate-limit.failed-logins-per-email.per=15m",
        "stockflow.security.rate-limit.failed-logins-per-ip.attempts=5",
        "stockflow.security.rate-limit.failed-logins-per-ip.per=10m",
        "stockflow.security.rate-limit.registrations-per-ip.attempts=2",
        "stockflow.security.rate-limit.registrations-per-ip.per=1h"})
class AuthRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final String WRONG_PASSWORD = "not-the-password-1";

    @Test
    void afterTooManyFailuresForOneEmailEvenTheRightPasswordWaits() throws Exception {
        String email = registered("ada");
        for (int i = 0; i < 3; i++) {
            login(newIp(), email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        // From yet another IP, with the correct password: the account is temporarily protected, and the
        // answer does not reveal that the password was right.
        login(newIp(), email, TEST_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"));
    }

    @Test
    void successfulLoginsDoNotCount() throws Exception {
        String email = registered("bob");
        String ip = newIp();
        for (int i = 0; i < 10; i++) {
            login(ip, email, TEST_PASSWORD).andExpect(status().isOk());
        }
        for (int i = 0; i < 3; i++) {
            login(ip, email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(ip, email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void unknownEmailsAreLimitedExactlyLikeRealOnes() throws Exception {
        String nobody = "nobody-" + NEXT_IP.get() + "@example.com";
        for (int i = 0; i < 3; i++) {
            login(newIp(), nobody, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(newIp(), nobody, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void oneIpTryingManyAccountsIsStoppedWhileOtherIpsAreNot() throws Exception {
        String attacker = newIp();
        for (int i = 0; i < 5; i++) {
            login(attacker, "victim" + i + "-" + attacker + "@example.com", WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized());
        }
        String email = registered("carol");
        login(attacker, email, TEST_PASSWORD).andExpect(status().isTooManyRequests());
        login(newIp(), email, TEST_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void theLimitRecoversOverTime() throws Exception {
        String email = registered("dave");
        for (int i = 0; i < 3; i++) {
            login(newIp(), email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        login(newIp(), email, TEST_PASSWORD).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(5));
        login(newIp(), email, TEST_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void registrationsArePerIp() throws Exception {
        String ip = newIp();
        register(ip, "eve1-" + ip + "@example.com").andExpect(status().isCreated());
        register(ip, "eve2-" + ip + "@example.com").andExpect(status().isCreated());
        register(ip, "eve3-" + ip + "@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1800"));

        register(newIp(), "eve3-" + ip + "@example.com").andExpect(status().isCreated());
    }

    private String registered(String name) throws Exception {
        String email = name + "-" + NEXT_IP.get() + "@example.com";
        createCustomer(email);
        return email;
    }

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }

    private ResultActions register(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "fullName": "Rate Limit Test"}
                        """.formatted(email, TEST_PASSWORD)));
    }

    /** A fresh documentation-range address (RFC 5737) per call. */
    private static String newIp() {
        int n = NEXT_IP.getAndIncrement();
        return "203.0." + (n / 250) + "." + (n % 250 + 1);
    }
}
