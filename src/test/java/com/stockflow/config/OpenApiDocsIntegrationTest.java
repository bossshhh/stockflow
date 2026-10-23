package com.stockflow.config;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.stockflow.AbstractIntegrationTest;

class OpenApiDocsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void apiDocsDescribeEveryEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StockFlow API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/products")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/inventory/{productId}/adjustments")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/orders/{id}/cancel")))
                // Pageable is expanded into page/size/sort query parameters, not one JSON object.
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.parameters[?(@.name == 'size')]").exists());
    }

    @Test
    void contractIsStrictEnoughForGeneratedClients() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                // Always-present fields are required; genuinely optional ones are nullable instead.
                .andExpect(jsonPath("$.components.schemas.OrderResponse.required", hasItem("status")))
                .andExpect(jsonPath("$.components.schemas.OrderResponse.required", not(hasItem("confirmedAt"))))
                .andExpect(jsonPath("$.components.schemas.OrderResponse.properties.confirmedAt.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.CreateProductRequest.required", not(hasItem("description"))))
                // Every operation documents the ApiError body, and responses are JSON.
                .andExpect(jsonPath("$.components.schemas.ApiError.required", hasItem("code")))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses.default.content['application/json']"
                        + ".schema.$ref").value("#/components/schemas/ApiError"))
                // Documented status codes match the real ones (201 for creation, 204 for deletion).
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['201'].content['application/json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].delete.responses['204']").exists());
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
