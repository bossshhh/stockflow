package com.stockflow.inventory;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.order.OrderService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;

class InventoryReportsIntegrationTest extends AbstractIntegrationTest {

    private static final String INVENTORY = "/api/v1/inventory";

    @Autowired
    private OrderService orderService;

    private long customerId;

    @BeforeEach
    void actAsStaff() {
        mockMvc = mvcAs(staff());
        customerId = createCustomer("ada@example.com");
    }

    @Test
    void lowStockListsActiveProductsAtOrBelowThresholdLowestFirst() throws Exception {
        productWithStock("PLENTY", "1.00", 50);
        productWithStock("EXACT", "1.00", 5);
        long reservedDown = productWithStock("RESERVED", "1.00", 8);
        productWithStock("EMPTY", "1.00", 0);
        long retired = productWithStock("RETIRED", "1.00", 0);
        mockMvc.perform(delete("/api/v1/products/" + retired)).andExpect(status().isNoContent());
        // 8 on hand, 6 reserved: only 2 available, so it is low stock even though on-hand looks fine.
        orderService.create(asCustomer(customerId), new CreateOrderRequest(
                List.of(new OrderItemRequest(reservedDown, 6))));

        mockMvc.perform(get(INVENTORY + "/low-stock").param("threshold", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].sku", contains("EMPTY", "RESERVED", "EXACT")))
                .andExpect(jsonPath("$.content[1].available").value(2));
    }

    @Test
    void lowStockThresholdMustNotBeNegative() throws Exception {
        mockMvc.perform(get(INVENTORY + "/low-stock").param("threshold", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("threshold"));
    }

    @Test
    void valuationOfEmptyInventoryIsZero() throws Exception {
        mockMvc.perform(get(INVENTORY + "/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(0))
                .andExpect(jsonPath("$.totalOnHand").value(0))
                .andExpect(jsonPath("$.totalValue").value(0.00));
    }

    @Test
    void valuationSumsUnitsAndValueAtCurrentPrices() throws Exception {
        long apple = productWithStock("APPLE", "1.50", 10);
        productWithStock("PEAR", "2.25", 4);
        orderService.create(asCustomer(customerId), new CreateOrderRequest(
                List.of(new OrderItemRequest(apple, 3))));

        mockMvc.perform(get(INVENTORY + "/valuation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(2))
                .andExpect(jsonPath("$.totalOnHand").value(14))
                .andExpect(jsonPath("$.totalReserved").value(3))
                .andExpect(jsonPath("$.totalAvailable").value(11))
                .andExpect(jsonPath("$.totalValue").value(24.00));
    }

    @Test
    void movementsCanBeFilteredByType() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        orderService.create(asCustomer(customerId), new CreateOrderRequest(
                List.of(new OrderItemRequest(apple, 2))));

        mockMvc.perform(get(INVENTORY + "/" + apple + "/movements").param("type", "RESERVATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("RESERVATION"));
        mockMvc.perform(get(INVENTORY + "/" + apple + "/movements"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(INVENTORY + "/" + apple + "/movements").param("type", "STOLEN"))
                .andExpect(status().isBadRequest());
    }
}
