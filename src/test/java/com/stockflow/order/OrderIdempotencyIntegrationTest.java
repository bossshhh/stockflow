package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;
import com.stockflow.common.web.ApiHeaders;

/**
 * A retried "place order" (double click, network retry) with the same Idempotency-Key must return the
 * order it already created instead of creating and reserving a second one.
 */
class OrderIdempotencyIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String KEY = "checkout-7f3a9c2e";

    private MockMvc customerMvc;
    private long apple;

    @BeforeEach
    void setUp() {
        customerMvc = mvcAs(customer(createCustomer("ada@example.com")));
        apple = productWithStock("APPLE", "1.00", 10);
    }

    @Test
    void retryWithTheSameKeyReturnsTheOriginalOrderAndReservesOnlyOnce() throws Exception {
        long first = orderId(placeOrder(KEY, items(apple, 3))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(ApiHeaders.IDEMPOTENT_REPLAYED)));

        placeOrder(KEY, items(apple, 3))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiHeaders.IDEMPOTENT_REPLAYED, "true"))
                .andExpect(jsonPath("$.id").value(first));

        assertThat(reserved(apple)).isEqualTo(3);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void replayShowsTheOrderAsItIsNow() throws Exception {
        long id = orderId(placeOrder(KEY, items(apple, 1)));
        mvcAs(staff()).perform(post(ORDERS + "/" + id + "/confirm")).andExpect(status().isOk());

        placeOrder(KEY, items(apple, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void itemsAreComparedAfterMergingDuplicates() throws Exception {
        long id = orderId(placeOrder(KEY, """
                {"items": [{"productId": %d, "quantity": 1}, {"productId": %d, "quantity": 1}]}
                """.formatted(apple, apple)));

        placeOrder(KEY, items(apple, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void sameKeyWithDifferentItemsIsRejected() throws Exception {
        placeOrder(KEY, items(apple, 1)).andExpect(status().isCreated());

        placeOrder(KEY, items(apple, 2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(reserved(apple)).isEqualTo(1);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void keysBelongToOneCustomer() throws Exception {
        long adas = orderId(placeOrder(KEY, items(apple, 1)));
        MockMvc bob = mvcAs(customer(createCustomer("bob@example.com")));

        long bobs = orderId(bob.perform(post(ORDERS).header(ApiHeaders.IDEMPOTENCY_KEY, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(items(apple, 1)))
                .andExpect(status().isCreated()));

        assertThat(bobs).isNotEqualTo(adas);
        assertThat(reserved(apple)).isEqualTo(2);
    }

    @Test
    void keyIsValidForExactly24Hours() throws Exception {
        long id = orderId(placeOrder(KEY, items(apple, 1)));

        clock.advance(Duration.ofHours(24));
        placeOrder(KEY, items(apple, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        clock.advance(Duration.ofMillis(1));
        long newId = orderId(placeOrder(KEY, items(apple, 1)).andExpect(status().isCreated()));
        assertThat(newId).isNotEqualTo(id);
        assertThat(reserved(apple)).isEqualTo(2);
    }

    @Test
    void failedAttemptDoesNotUseUpTheKey() throws Exception {
        placeOrder(KEY, items(apple, 11))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mvcAs(staff()).perform(post("/api/v1/inventory/" + apple + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityChange": 5, "reason": "Delivery"}
                                """))
                .andExpect(status().isOk());

        placeOrder(KEY, items(apple, 11)).andExpect(status().isCreated());
        assertThat(reserved(apple)).isEqualTo(11);
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        for (String key : new String[] {"short", "has spaces in it", "ümlaut-key-123", "x".repeat(65)}) {
            placeOrder(key, items(apple, 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(orderCount()).isZero();
    }

    private ResultActions placeOrder(String key, String body) throws Exception {
        return customerMvc.perform(post(ORDERS).header(ApiHeaders.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String items(long productId, int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    private static long orderId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long orderCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Long.class);
    }
}
