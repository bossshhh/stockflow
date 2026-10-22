package com.stockflow.order;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.TestClockConfiguration;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;

/**
 * Fixture, one hour apart (T = test clock start):
 * <pre>
 *   #1 T+0h  ada@example.com    PENDING     2 x 10.00 = 20.00
 *   #2 T+1h  bob@example.com    CONFIRMED   3 x 10.00 = 30.00
 *   #3 T+2h  Ada@Example.com    CANCELLED   1 x 10.00 = 10.00
 *   #4 T+3h  bob@example.com    CONFIRMED   5 x 10.00 = 50.00
 * </pre>
 */
class OrderSearchIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final Instant T = TestClockConfiguration.START;

    @Autowired
    private OrderService orderService;

    @BeforeEach
    void createOrders() {
        long product = productWithStock("WID-001", "10.00", 100);
        placeOrder(product, "ada@example.com", 2);
        clock.advance(Duration.ofHours(1));
        orderService.confirm(placeOrder(product, "bob@example.com", 3));
        clock.advance(Duration.ofHours(1));
        orderService.cancel(placeOrder(product, "Ada@Example.com", 1));
        clock.advance(Duration.ofHours(1));
        orderService.confirm(placeOrder(product, "bob@example.com", 5));
    }

    @Test
    void noFiltersReturnsAllNewestFirstWithoutItems() throws Exception {
        mockMvc.perform(get(ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].id", contains(4, 3, 2, 1)))
                .andExpect(jsonPath("$.content[0].items").doesNotExist());
    }

    @Test
    void filtersByStatus() throws Exception {
        mockMvc.perform(get(ORDERS).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.content[*].id", contains(4, 2)));
    }

    @Test
    void filtersByEmailIgnoringCase() throws Exception {
        mockMvc.perform(get(ORDERS).param("customerEmail", "ADA@example.COM"))
                .andExpect(jsonPath("$.content[*].id", contains(3, 1)));
    }

    @Test
    void dateRangeIncludesStartAndExcludesEnd() throws Exception {
        mockMvc.perform(get(ORDERS)
                        .param("createdFrom", T.plus(Duration.ofHours(1)).toString())
                        .param("createdTo", T.plus(Duration.ofHours(3)).toString()))
                .andExpect(jsonPath("$.content[*].id", contains(3, 2)));
    }

    @Test
    void filtersCombine() throws Exception {
        mockMvc.perform(get(ORDERS)
                        .param("status", "CONFIRMED")
                        .param("customerEmail", "bob@example.com")
                        .param("createdFrom", T.plus(Duration.ofHours(2)).toString()))
                .andExpect(jsonPath("$.content[*].id", contains(4)));
    }

    @Test
    void pagingAndSortingWork() throws Exception {
        mockMvc.perform(get(ORDERS).param("sort", "totalAmount,desc").param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].id", contains(4, 2)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidInputsReturn400() throws Exception {
        mockMvc.perform(get(ORDERS).param("status", "SHIPPED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get(ORDERS).param("createdFrom", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get(ORDERS).param("sort", "customerEmail"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get(ORDERS)
                        .param("createdFrom", T.plus(Duration.ofHours(2)).toString())
                        .param("createdTo", T.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void summaryCountsEveryStatusAndOnlyConfirmedRevenue() throws Exception {
        mockMvc.perform(get(ORDERS + "/summary")
                        .param("from", T.toString())
                        .param("to", T.plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(4))
                .andExpect(jsonPath("$.ordersByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.ordersByStatus.CONFIRMED").value(2))
                .andExpect(jsonPath("$.ordersByStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.confirmedRevenue").value(80.00));
    }

    @Test
    void summaryOfEmptyPeriodIsAllZeros() throws Exception {
        mockMvc.perform(get(ORDERS + "/summary")
                        .param("from", T.minus(Duration.ofDays(2)).toString())
                        .param("to", T.minus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.ordersByStatus.CONFIRMED").value(0))
                .andExpect(jsonPath("$.confirmedRevenue").value(0.00));
    }

    @Test
    void summaryRequiresBothDates() throws Exception {
        mockMvc.perform(get(ORDERS + "/summary").param("from", T.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private long placeOrder(long productId, String email, int quantity) {
        return orderService.create(new CreateOrderRequest("Customer", email,
                List.of(new OrderItemRequest(productId, quantity)))).id();
    }
}
