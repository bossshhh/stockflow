package com.stockflow.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.common.web.ApiHeaders;

/** The role matrix from SecurityConfig, checked rule by rule. */
class SecurityRulesIntegrationTest extends AbstractIntegrationTest {

    private static final String PRODUCT_JSON = "{\"sku\": \"SKU-1\", \"name\": \"Thing\", \"price\": 1}";
    private static final String ADJUST_JSON = "{\"quantityChange\": 1, \"reason\": \"x\"}";

    @Test
    void anonymousGets401WithApiErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/products"));
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        // Reaching validation (400) proves the request got past security.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCanBrowseProductsButNotChangeThem() throws Exception {
        long me = createCustomer("ada@example.com");

        mockMvc.perform(get("/api/v1/products").with(customer(me))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products").with(customer(me))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void customerCannotSeeInventoryConfirmOrdersOrReports() throws Exception {
        long me = createCustomer("ada@example.com");

        mockMvc.perform(get("/api/v1/inventory").with(customer(me))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventory/1/adjustments").with(customer(me))
                        .contentType(MediaType.APPLICATION_JSON).content(ADJUST_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/confirm").with(customer(me))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/summary").with(customer(me))
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-02-01T00:00:00Z"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotPlaceOrdersOrManageAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(staff())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\": []}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users").with(staff())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanManageProductsAndStock() throws Exception {
        mockMvc.perform(post("/api/v1/products").with(staff())
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inventory/1/adjustments").with(staff())
                        .contentType(MediaType.APPLICATION_JSON).content(ADJUST_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateStaffButNotCustomers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "sam@stockflow.local", "password": "%s", "fullName": "Sam", "role": "STAFF"}
                        """.formatted(TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"));
        mockMvc.perform(post("/api/v1/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "wes@stockflow.local", "password": "%s", "fullName": "Wes", "role": "WAREHOUSE"}
                        """.formatted(TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("WAREHOUSE"));
        mockMvc.perform(post("/api/v1/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "cat@example.com", "password": "%s", "fullName": "Cat", "role": "CUSTOMER"}
                        """.formatted(TEST_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/products")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mockMvc.perform(options("/api/v1/products")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void corsLetsTheUiSendAnIdempotencyKeyAndReadTheReplayHeader() throws Exception {
        mockMvc.perform(options("/api/v1/orders")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase(ApiHeaders.IDEMPOTENCY_KEY)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString(ApiHeaders.IDEMPOTENT_REPLAYED)));
    }
}
