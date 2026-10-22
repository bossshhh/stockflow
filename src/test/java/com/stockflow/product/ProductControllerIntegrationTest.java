package com.stockflow.product;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;

class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    /** Product and stock management is staff work; access rules are tested in SecurityRulesIntegrationTest. */
    @BeforeEach
    void actAsStaff() {
        mockMvc = mvcAs(staff());
    }

    private static final String PRODUCTS = "/api/v1/products";

    @Test
    void createReturns201WithLocationAndNormalizedSku() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "wid-001", "name": "Widget", "description": "A widget", "price": 19.9}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(PRODUCTS + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sku").value("WID-001"))
                .andExpect(jsonPath("$.price").value(19.90))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void duplicateSkuIsRejectedCaseInsensitively() throws Exception {
        createProduct("WID-001", "10.00");

        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "wid-001", "name": "Other", "price": 5}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"))
                .andExpect(jsonPath("$.path").value(PRODUCTS));
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "a b", "name": "", "price": -1.999}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'sku')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'price')]").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("{\"sku\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void getUnknownProductReturns404() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get(PRODUCTS + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void listIsPagedAndSorted() throws Exception {
        createProduct("SKU-C", "3.00");
        createProduct("SKU-A", "1.00");
        createProduct("SKU-B", "2.00");

        mockMvc.perform(get(PRODUCTS).param("page", "0").param("size", "2").param("sort", "sku,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].sku").value("SKU-A"))
                .andExpect(jsonPath("$.content[1].sku").value("SKU-B"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(get(PRODUCTS).param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message", containsString("nope")));
    }

    @Test
    void updateReplacesEditableFields() throws Exception {
        long id = createProduct("WID-001", "10.00");

        mockMvc.perform(put(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Widget v2", "description": "Better", "price": 12.5}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("WID-001"))
                .andExpect(jsonPath("$.name").value("Widget v2"))
                .andExpect(jsonPath("$.price").value(12.50));
    }

    @Test
    void deleteDeactivatesAndActiveFilterHidesIt() throws Exception {
        long id = createProduct("WID-001", "10.00");
        createProduct("WID-002", "10.00");

        mockMvc.perform(delete(PRODUCTS + "/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get(PRODUCTS).param("active", "true"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("WID-002"));
    }

    @Test
    void unknownPathReturnsApiError() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private long createProduct(String sku, String price) throws Exception {
        String body = mockMvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "%s", "name": "Product %s", "price": %s}
                        """.formatted(sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
