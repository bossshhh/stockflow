package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.user.CurrentUser;
import com.stockflow.user.Role;

/** Step 7.5: PACKED -> SHIPPED -> DELIVERED, the WAREHOUSE role, and the status history. */
class FulfilmentIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    @Autowired
    private OrderService orderService;

    private long customerId;
    private long apple;
    private long orderId;

    @BeforeEach
    void confirmedOrder() {
        customerId = createCustomer("ada@example.com");
        apple = productWithStock("APPLE", "2.50", 10);
        orderId = orderService.create(asCustomer(customerId),
                new CreateOrderRequest(List.of(new OrderItemRequest(apple, 4)), TestAddresses.ADDRESS)).id();
        orderService.confirm(STAFF_ACTOR, orderId);
    }

    @Test
    void warehouseFulfilsTheOrderAndTheCustomerSeesEveryCheckpoint() throws Exception {
        clock.advance(Duration.ofMinutes(10));
        action("pack", warehouseActor()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PACKED"));
        clock.advance(Duration.ofHours(2));
        action("ship", warehouseActor()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));
        clock.advance(Duration.ofDays(1));
        action("deliver", warehouseActor()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(get(ORDERS + "/" + orderId).with(customer(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].status", contains("PENDING", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.history[4].at").value(clock.instant().toString()))
                .andExpect(jsonPath("$.history[0].actorId").doesNotExist());
        // Fulfilment never touches stock again: it left on-hand stock at confirmation.
        assertThat(onHand(apple)).isEqualTo(6);
        assertThat(reserved(apple)).isZero();
    }

    @Test
    void stagesMustHappenInOrder() throws Exception {
        action("ship", warehouseActor()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        action("deliver", warehouseActor()).andExpect(status().isConflict());
        action("pack", warehouseActor()).andExpect(status().isOk());
        action("pack", warehouseActor()).andExpect(status().isConflict());
    }

    @Test
    void packedOrderCanNoLongerBeCancelledEvenInsideTheFiveMinuteWindow() throws Exception {
        action("pack", warehouseActor()).andExpect(status().isOk());

        mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").with(customer(customerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        mockMvc.perform(post(ORDERS + "/" + orderId + "/cancel").with(staff()))
                .andExpect(status().isConflict());
        assertThat(onHand(apple)).isEqualTo(6);
    }

    @Test
    void staffCanFulfilToo() throws Exception {
        action("pack", staff()).andExpect(status().isOk());
        action("ship", admin()).andExpect(status().isOk());
    }

    @Test
    void warehouseCanFulfilAndReadStockButNothingElse() throws Exception {
        long pending = orderService.create(asCustomer(customerId),
                new CreateOrderRequest(List.of(new OrderItemRequest(apple, 1)), TestAddresses.ADDRESS)).id();

        mockMvc.perform(post(ORDERS + "/" + pending + "/confirm").with(warehouse())).andExpect(status().isForbidden());
        mockMvc.perform(post(ORDERS + "/" + pending + "/cancel").with(warehouse())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inventory/" + apple).with(warehouse())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/" + apple + "/adjustments").with(warehouse())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantityChange\": 1, \"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products").with(warehouse())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\": \"W-1\", \"name\": \"x\", \"price\": 1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ORDERS + "/summary").with(warehouse())
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-02-01T00:00:00Z"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ORDERS).with(warehouse())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void customerCannotFulfilTheirOwnOrder() throws Exception {
        action("pack", customer(customerId)).andExpect(status().isForbidden());
    }

    @Test
    void revenueKeepsCountingOrdersAsTheyMoveThroughFulfilment() throws Exception {
        String from = clock.instant().minus(Duration.ofDays(1)).toString();
        String to = clock.instant().plus(Duration.ofDays(1)).toString();
        action("pack", warehouseActor()).andExpect(status().isOk());
        action("ship", warehouseActor()).andExpect(status().isOk());

        mockMvc.perform(get(ORDERS + "/summary").with(staff()).param("from", from).param("to", to))
                .andExpect(jsonPath("$.ordersByStatus.SHIPPED").value(1))
                .andExpect(jsonPath("$.confirmedRevenue").value(10.00));
    }

    @Test
    void concurrentPacksOfOneOrderRecordItOnce() throws Exception {
        CurrentUser worker = new CurrentUser(WAREHOUSE_ID, Role.WAREHOUSE);
        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        orderService.pack(worker, orderId);
                        return true;
                    } catch (InvalidOrderStateException ex) {
                        return false;
                    }
                }));
            }
            startGate.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM order_status_events WHERE order_id = ? AND status = 'PACKED'",
                    Long.class, orderId)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private ResultActions action(String name, RequestPostProcessor actor) throws Exception {
        return mockMvc.perform(post(ORDERS + "/" + orderId + "/" + name).with(actor));
    }

    private static RequestPostProcessor warehouseActor() {
        return warehouse();
    }
}
