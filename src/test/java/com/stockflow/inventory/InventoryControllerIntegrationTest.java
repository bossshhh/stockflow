package com.stockflow.inventory;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;

class InventoryControllerIntegrationTest extends AbstractIntegrationTest {

    /** Product and stock management is staff work; access rules are tested in SecurityRulesIntegrationTest. */
    @BeforeEach
    void actAsStaff() {
        mockMvc = mvcAs(staff());
    }

    private static final String INVENTORY = "/api/v1/inventory";

    @Test
    void newProductStartsWithEmptyStock() throws Exception {
        long id = createProduct("WID-001");

        mockMvc.perform(get(INVENTORY + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("WID-001"))
                .andExpect(jsonPath("$.onHand").value(0))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void adjustmentsChangeStockAndAreRecordedNewestFirst() throws Exception {
        long id = createProduct("WID-001");

        adjust(id, 10, "Delivery from supplier").andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.available").value(10));
        adjust(id, -4, "Damaged in storage").andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(6));

        mockMvc.perform(get(INVENTORY + "/" + id + "/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].onHandChange").value(-4))
                .andExpect(jsonPath("$.content[0].onHandAfter").value(6))
                .andExpect(jsonPath("$.content[0].reason").value("Damaged in storage"))
                .andExpect(jsonPath("$.content[1].onHandChange").value(10));
    }

    @Test
    void removingMoreThanOnHandIsRejectedAndChangesNothing() throws Exception {
        long id = createProduct("WID-001");
        adjust(id, 5, "Delivery");

        adjust(id, -6, "Too many").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(get(INVENTORY + "/" + id)).andExpect(jsonPath("$.onHand").value(5));
        mockMvc.perform(get(INVENTORY + "/" + id + "/movements")).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cannotRemoveStockThatIsReservedForOrders() throws Exception {
        long id = createProduct("WID-001");
        adjust(id, 10, "Delivery");
        // Orders do not exist yet (step 3), so simulate 8 reserved units directly.
        jdbcTemplate.update("UPDATE inventory SET reserved = 8 WHERE product_id = ?", id);

        adjust(id, -3, "Would leave 7 on hand for 8 reserved").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        adjust(id, -2, "Leaves exactly the reserved 8").andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(8))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void zeroAdjustmentIsRejected() throws Exception {
        long id = createProduct("WID-001");

        adjust(id, 0, "Nothing").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void adjustmentRequiresQuantityAndReason() throws Exception {
        long id = createProduct("WID-001");

        mockMvc.perform(post(INVENTORY + "/" + id + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'quantityChange')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reason')]").exists());
    }

    @Test
    void unknownProductReturns404Everywhere() throws Exception {
        mockMvc.perform(get(INVENTORY + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        adjust(999, 5, "Delivery").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get(INVENTORY + "/999/movements"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listShowsStockForEveryProduct() throws Exception {
        long first = createProduct("WID-001");
        createProduct("WID-002");
        adjust(first, 3, "Delivery");

        mockMvc.perform(get(INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].sku").value("WID-001"))
                .andExpect(jsonPath("$.content[0].onHand").value(3))
                .andExpect(jsonPath("$.content[1].sku").value("WID-002"))
                .andExpect(jsonPath("$.content[1].onHand").value(0));
    }

    private ResultActions adjust(long productId, int quantityChange, String reason) throws Exception {
        return mockMvc.perform(post(INVENTORY + "/" + productId + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"quantityChange": %d, "reason": "%s"}
                        """.formatted(quantityChange, reason)));
    }

    private long createProduct(String sku) throws Exception {
        String body = mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "%s", "name": "Product %s", "price": 10}
                        """.formatted(sku, sku)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
