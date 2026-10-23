package com.stockflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression tests for the bugs found by the QA session after step 7.0 (boundary-value and input-coercion
 * attacks against the running system). Each test reproduced its bug before the fix.
 */
class QaRegressionIntegrationTest extends AbstractIntegrationTest {

    private static final String MAX_PRICE = "9999999999.99";
    private static final int MAX_QUANTITY = 10_000;
    /** The stock ceiling; keeps on_hand + any single change far below PostgreSQL's INTEGER maximum. */
    private static final int MAX_STOCK = 1_000_000_000;

    private MockMvc customerMvc;
    private MockMvc staffMvc;

    @BeforeEach
    void actors() {
        customerMvc = mvcAs(customer(createCustomer("qa@example.com")));
        staffMvc = mvcAs(staff());
    }

    /** Bug 1: the largest order the validation rules allow overflowed NUMERIC(14,2) and returned 500. */
    @Test
    void largestAllowedOrderLineIsStoredWithExactTotal() throws Exception {
        long product = productWithStock("MAXPRICE", MAX_PRICE, MAX_QUANTITY);

        order("""
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(product, MAX_QUANTITY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(99_999_999_999_900.00));
    }

    /** Bug 2: repeated adjustments pushed on_hand past INTEGER max and the UPDATE failed with a 500. */
    @Test
    void stockCannotGrowPastTheCeilingAndSaysWhy() throws Exception {
        long product = productWithStock("BIGSTOCK", "1.00", 0);
        jdbcTemplate.update("UPDATE inventory SET on_hand = ? WHERE product_id = ?", MAX_STOCK - 10, product);

        adjust(product, 11)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_LIMIT_EXCEEDED"));
        adjust(product, 10).andExpect(status().isOk()).andExpect(jsonPath("$.onHand").value(MAX_STOCK));
        assertThat(onHand(product)).isEqualTo(MAX_STOCK);
    }

    /**
     * Bug 2b (found while re-testing the fix for bug 2): a row already above the ceiling (created before the
     * ceiling existed) could not be reduced either, so it could never be corrected through the API.
     */
    @Test
    void stockAboveTheCeilingCanStillBeReduced() throws Exception {
        long product = productWithStock("LEGACY", "1.00", 0);
        jdbcTemplate.update("UPDATE inventory SET on_hand = ? WHERE product_id = ?", 2_147_000_000, product);

        adjust(product, -1_000_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(2_146_000_000));
        adjust(product, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_LIMIT_EXCEEDED"));
    }

    /** Bug 3: Jackson silently truncated 1.5 to 1, so the customer got an order they did not ask for. */
    @Test
    void fractionalQuantityIsRejectedNotTruncated() throws Exception {
        long product = productWithStock("APPLE", "1.00", 10);

        order("""
                {"items": [{"productId": %d, "quantity": 1.5}]}
                """.formatted(product))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(reserved(product)).isZero();
    }

    /** Bug 4: numbers sent as JSON strings were silently converted. The contract says number. */
    @Test
    void numbersSentAsStringsAreRejected() throws Exception {
        staffMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"STR-1\", \"name\": \"x\", \"price\": \"12.50\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    /** Bug 5: duplicate lines were merged AFTER validation, so 6,000 + 6,000 bypassed the 10,000 limit. */
    @Test
    void mergedDuplicateLinesMustRespectTheQuantityLimit() throws Exception {
        long product = productWithStock("APPLE", "1.00", 20_000);

        order("""
                {"items": [{"productId": %d, "quantity": 6000}, {"productId": %d, "quantity": 6000}]}
                """.formatted(product, product))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        assertThat(reserved(product)).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions order(String json) throws Exception {
        return customerMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions adjust(long productId, int quantityChange)
            throws Exception {
        return staffMvc.perform(post("/api/v1/inventory/" + productId + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityChange\": %d, \"reason\": \"qa\"}".formatted(quantityChange)));
    }
}
