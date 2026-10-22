package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;

/** The finding from the step 3 security review: one customer must not see or touch another's orders. */
class OrderOwnershipIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    private long ada;
    private long bob;
    private long adasOrder;
    private long apple;

    @BeforeEach
    void twoCustomersOneOrder() {
        ada = createCustomer("ada@example.com");
        bob = createCustomer("bob@example.com");
        apple = productWithStock("APPLE", "1.00", 10);
        adasOrder = orderService.create(asCustomer(ada),
                new CreateOrderRequest(List.of(new OrderItemRequest(apple, 2)))).id();
    }

    @Test
    void ownerCanSeeTheirOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + adasOrder).with(customer(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(ada));
    }

    @Test
    void otherCustomerGets404NotForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + adasOrder).with(customer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void otherCustomerCannotCancelIt() throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + adasOrder + "/cancel").with(customer(bob)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/orders/" + adasOrder).with(customer(ada)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(reserved(apple)).isEqualTo(2);
    }

    @Test
    void otherCustomersSearchDoesNotIncludeIt() throws Exception {
        mockMvc.perform(get("/api/v1/orders").with(customer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staffSeeEveryOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + adasOrder).with(staff())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders").with(staff())).andExpect(jsonPath("$.totalElements").value(1));
    }
}
