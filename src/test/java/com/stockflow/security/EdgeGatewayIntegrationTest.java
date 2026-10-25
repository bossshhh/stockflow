package com.stockflow.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.stockflow.AbstractIntegrationTest;

/**
 * The edge check as deployed on Vercel: only requests carrying the secret the Vercel middleware adds get in,
 * and the client address the middleware forwards drives the per-IP login limit (2 failures per 10 min here).
 * The actuator runs on the main port in this class, as on Vercel (one port).
 */
@TestPropertySource(properties = {
        "stockflow.security.edge.secret=" + EdgeGatewayIntegrationTest.SECRET,
        "management.server.port=",
        "stockflow.security.rate-limit.failed-logins-per-ip.attempts=2",
        "stockflow.security.rate-limit.failed-logins-per-ip.per=10m",
        "stockflow.security.rate-limit.failed-logins-per-email.attempts=100",
        "stockflow.security.rate-limit.failed-logins-per-email.per=15m"})
class EdgeGatewayIntegrationTest extends AbstractIntegrationTest {

    static final String SECRET = "test-edge-secret-0123456789abcdef-0123";
    private static final String WRONG_PASSWORD = "not-the-password-1";

    @Test
    void requestWithoutSecretIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void wrongSecretIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/catalog").header(EdgeGatewayFilter.SECRET_HEADER, SECRET + "x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/catalog")
                        .header(EdgeGatewayFilter.SECRET_HEADER, SECRET.substring(0, SECRET.length() - 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rightSecretPasses() throws Exception {
        mockMvc.perform(edge(get("/api/v1/catalog"))).andExpect(status().isOk());
    }

    @Test
    void healthCheckNeedsNoSecret() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void eachClientIpGetsItsOwnLoginLimit() throws Exception {
        String email = "edge-limit@example.com";
        createCustomer(email);
        for (int i = 0; i < 2; i++) {
            login(edge(loginRequest(email)).header(EdgeGatewayFilter.CLIENT_IP_HEADER, "203.0.113.10"))
                    .andExpect(status().isUnauthorized());
        }
        login(edge(loginRequest(email)).header(EdgeGatewayFilter.CLIENT_IP_HEADER, "203.0.113.10"))
                .andExpect(status().isTooManyRequests());

        login(edge(loginRequest(email)).header(EdgeGatewayFilter.CLIENT_IP_HEADER, "203.0.113.11"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutClientIpHeaderTheConnectionAddressIsUsed() throws Exception {
        String email = "edge-noip@example.com";
        createCustomer(email);
        for (int i = 0; i < 2; i++) {
            login(edge(loginRequest(email)).with(fromAddress("198.51.100.7"))).andExpect(status().isUnauthorized());
        }
        login(edge(loginRequest(email)).with(fromAddress("198.51.100.7"))).andExpect(status().isTooManyRequests());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-ip", "example.com", "beef.cafe", "1.2.3.4.5", "1.2.3", "256.1.1.1", "1.2.3.4, 5.6.7.8",
            "", "1.2.3.4\n",
            "111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111"})
    void malformedClientIpIsRejected(String clientIp) throws Exception {
        mockMvc.perform(edge(get("/api/v1/catalog")).header(EdgeGatewayFilter.CLIENT_IP_HEADER, clientIp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void ipv6ClientIpIsAccepted() throws Exception {
        mockMvc.perform(edge(get("/api/v1/catalog")).header(EdgeGatewayFilter.CLIENT_IP_HEADER, "2001:db8::1"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder edge(MockHttpServletRequestBuilder request) {
        return request.header(EdgeGatewayFilter.SECRET_HEADER, SECRET);
    }

    private static MockHttpServletRequestBuilder loginRequest(String email) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, WRONG_PASSWORD));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromAddress(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions login(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }
}
