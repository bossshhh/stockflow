package com.stockflow.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.stockflow.AbstractIntegrationTest;

/** Without an edge secret (local, Compose, CI, the VPS kit) nothing changes, and the client-IP header is ignored. */
@TestPropertySource(properties = {
        "stockflow.security.rate-limit.failed-logins-per-ip.attempts=2",
        "stockflow.security.rate-limit.failed-logins-per-ip.per=10m",
        "stockflow.security.rate-limit.failed-logins-per-email.attempts=100",
        "stockflow.security.rate-limit.failed-logins-per-email.per=15m"})
class EdgeGatewayDisabledIntegrationTest extends AbstractIntegrationTest {

    @Test
    void clientIpHeaderIsIgnoredWhenTheEdgeIsOff() throws Exception {
        createCustomer("edge-off@example.com");
        for (int i = 1; i <= 2; i++) {
            failedLogin("203.0.113." + i).andExpect(status().isUnauthorized());
        }
        // A third "visitor" by header only: same connection, so the same bucket.
        failedLogin("203.0.113.3").andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions failedLogin(String claimedIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr("198.51.100.20");
                    return request;
                })
                .header(EdgeGatewayFilter.CLIENT_IP_HEADER, claimedIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "edge-off@example.com", "password": "not-the-password-1"}
                        """));
    }
}
