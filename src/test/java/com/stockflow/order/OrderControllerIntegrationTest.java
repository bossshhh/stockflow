package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** The order lifecycle as the two real actors see it: a customer places and cancels, staff confirm. */
class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private MockMvc customerMvc;
    private MockMvc staffMvc;

    @BeforeEach
    void actors() {
        long customerId = createCustomer("ada@example.com");
        customerMvc = mvcAs(customer(customerId));
        staffMvc = mvcAs(staff());
    }

    @Test
    void createReservesStockAndTakesCustomerDetailsFromTheAccount() throws Exception {
        long apple = productWithStock("APPLE", "1.25", 10);
        long pear = productWithStock("PEAR", "2.00", 10);

        createOrder("""
                {"items": [{"productId": %d, "quantity": 1}, {"productId": %d, "quantity": 3},
                           {"productId": %d, "quantity": 1}]}
                """.formatted(pear, apple, pear))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(ORDERS + "/1")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerEmail").value("ada@example.com"))
                .andExpect(jsonPath("$.customerName").value("Customer ada@example.com"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].sku").value("APPLE"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[1].sku").value("PEAR"))
                .andExpect(jsonPath("$.items[1].quantity").value(2))
                .andExpect(jsonPath("$.totalAmount").value(7.75));

        assertThat(reserved(apple)).isEqualTo(3);
        assertThat(reserved(pear)).isEqualTo(2);
        assertThat(onHand(apple)).isEqualTo(10);
        staffMvc.perform(get("/api/v1/inventory/" + apple + "/movements"))
                .andExpect(jsonPath("$.content[0].type").value("RESERVATION"))
                .andExpect(jsonPath("$.content[0].orderId").value(1));
    }

    @Test
    void outOfStockItemRollsBackTheWholeOrder() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long pear = productWithStock("PEAR", "1.00", 1);

        createOrder(orderJson(apple, 2, pear, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message", containsString("PEAR")))
                .andExpect(jsonPath("$.message", not(containsString("on hand"))));

        // The apple reservation made before the pear failed was rolled back with the order.
        assertThat(reserved(apple)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Long.class)).isZero();
    }

    @Test
    void inactiveProductCannotBeOrdered() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        staffMvc.perform(delete("/api/v1/products/" + apple)).andExpect(status().isNoContent());

        createOrder(orderJson(apple, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        createOrder(orderJson(999, 1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void invalidOrderReturnsFieldErrors() throws Exception {
        createOrder("{\"items\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'items')]").exists());
        createOrder(orderJson(1, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].quantity"));
    }

    @Test
    void staffConfirmDeductsStockAndCannotBeRepeated() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = placeOrder(apple, 4);

        staffMvc.perform(post(ORDERS + "/" + orderId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value(clock.instant().toString()));
        assertThat(onHand(apple)).isEqualTo(6);
        assertThat(reserved(apple)).isZero();

        staffMvc.perform(post(ORDERS + "/" + orderId + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        assertThat(onHand(apple)).isEqualTo(6);
    }

    @Test
    void customerCancelsPendingOrderAndReservationIsReleased() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = placeOrder(apple, 4);

        customerMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(reserved(apple)).isZero();
        assertThat(onHand(apple)).isEqualTo(10);

        customerMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        staffMvc.perform(post(ORDERS + "/" + orderId + "/confirm"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmedOrderCanBeCancelledAtExactlyFiveMinutesAndIsRestocked() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = placeOrder(apple, 4);
        staffMvc.perform(post(ORDERS + "/" + orderId + "/confirm")).andExpect(status().isOk());

        clock.advance(WINDOW);

        customerMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(onHand(apple)).isEqualTo(10);
        staffMvc.perform(get("/api/v1/inventory/" + apple + "/movements"))
                .andExpect(jsonPath("$.content[0].type").value("RESTOCK"))
                .andExpect(jsonPath("$.content[0].onHandChange").value(4));
    }

    @Test
    void confirmedOrderCannotBeCancelledAfterFiveMinutes() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = placeOrder(apple, 4);
        staffMvc.perform(post(ORDERS + "/" + orderId + "/confirm")).andExpect(status().isOk());

        clock.advance(WINDOW.plusMillis(1));

        customerMvc.perform(post(ORDERS + "/" + orderId + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_EXPIRED"));
        assertThat(onHand(apple)).isEqualTo(6);
        customerMvc.perform(get(ORDERS + "/" + orderId)).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void orderKeepsThePriceFromWhenItWasPlaced() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = placeOrder(apple, 2);

        staffMvc.perform(put("/api/v1/products/" + apple).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Apple\", \"price\": 9.99}"))
                .andExpect(status().isOk());

        customerMvc.perform(get(ORDERS + "/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(1.00))
                .andExpect(jsonPath("$.totalAmount").value(2.00));
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        customerMvc.perform(get(ORDERS + "/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        staffMvc.perform(post(ORDERS + "/999/confirm")).andExpect(status().isNotFound());
        customerMvc.perform(post(ORDERS + "/999/cancel")).andExpect(status().isNotFound());
    }

    private long placeOrder(long productId, int quantity) throws Exception {
        String body = createOrder(orderJson(productId, quantity))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions createOrder(String json) throws Exception {
        return customerMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String orderJson(long productId, int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    private static String orderJson(long firstId, int firstQuantity, long secondId, int secondQuantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}, {"productId": %d, "quantity": %d}]}
                """.formatted(firstId, firstQuantity, secondId, secondQuantity);
    }
}
