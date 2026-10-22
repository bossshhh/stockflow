package com.stockflow.config;

import static org.hamcrest.Matchers.hasKey;
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
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
