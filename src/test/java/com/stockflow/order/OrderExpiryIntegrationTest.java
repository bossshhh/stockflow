package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.common.web.ApiHeaders;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.user.CurrentUser;

/**
 * PENDING orders that nobody confirms in time (48 h by default) are cancelled by the system, so abandoned
 * orders cannot hold stock forever. The job is called directly; scheduling is off in tests.
 */
class OrderExpiryIntegrationTest extends AbstractIntegrationTest {

    private static final Duration PENDING_EXPIRY = Duration.ofHours(48);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderHousekeeping housekeeping;

    private CurrentUser customer;
    private long customerId;
    private long apple;

    @BeforeEach
    void setUp() {
        customerId = createCustomer("ada@example.com");
        customer = asCustomer(customerId);
        apple = productWithStock("APPLE", "1.00", 10);
    }

    @Test
    void pendingOrderNobodyConfirmedIsCancelledByTheSystemAndItsStockReleased() {
        long id = place(3);
        clock.advance(PENDING_EXPIRY.plusMillis(1));

        assertThat(housekeeping.expireStalePendingOrders()).isEqualTo(1);

        assertThat(orderService.getById(STAFF_ACTOR, id).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reserved(apple)).isZero();
        assertThat(onHand(apple)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM order_status_events
                WHERE order_id = ? AND status = 'CANCELLED' AND actor_id IS NULL
                """, Long.class, id)).as("cancelled by the system: no actor").isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT type FROM stock_movements WHERE order_id = ? ORDER BY id DESC LIMIT 1", String.class, id))
                .isEqualTo("RELEASE");
    }

    @Test
    void anOrderExactlyAtTheLimitIsKept() {
        long id = place(3);
        clock.advance(PENDING_EXPIRY);

        assertThat(housekeeping.expireStalePendingOrders()).isZero();
        assertThat(orderService.getById(STAFF_ACTOR, id).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(reserved(apple)).isEqualTo(3);
    }

    @Test
    void onlyStaleOrdersAreCancelled() {
        long old = place(1);
        clock.advance(PENDING_EXPIRY.minusHours(1));
        long fresh = place(1);
        clock.advance(Duration.ofHours(2));

        assertThat(housekeeping.expireStalePendingOrders()).isEqualTo(1);
        assertThat(orderService.getById(STAFF_ACTOR, old).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderService.getById(STAFF_ACTOR, fresh).status()).isEqualTo(OrderStatus.PENDING);
        assertThat(reserved(apple)).isEqualTo(1);
    }

    @Test
    void ordersAlreadyConfirmedOrCancelledAreLeftAlone() {
        long confirmed = place(2);
        orderService.confirm(STAFF_ACTOR, confirmed);
        long cancelled = place(1);
        orderService.cancel(customer, cancelled);
        clock.advance(PENDING_EXPIRY.multipliedBy(2));

        assertThat(housekeeping.expireStalePendingOrders()).isZero();
        assertThat(orderService.getById(STAFF_ACTOR, confirmed).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(onHand(apple)).isEqualTo(8);
    }

    @Test
    void idempotencyKeysAreDeletedOnceTheyExpire() throws Exception {
        placeWithKey("first-checkout-key");
        clock.advance(Duration.ofHours(24).plusMillis(1));
        placeWithKey("second-checkout-key");

        assertThat(housekeeping.deleteExpiredIdempotencyKeys()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT idempotency_key FROM order_idempotency_keys", String.class))
                .containsExactly("second-checkout-key");
    }

    private long place(int quantity) {
        return orderService.create(customer, new CreateOrderRequest(List.of(new OrderItemRequest(apple, quantity)), TestAddresses.ADDRESS)).id();
    }

    private void placeWithKey(String key) throws Exception {
        mvcAs(customer(customerId)).perform(post("/api/v1/orders").header(ApiHeaders.IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestAddresses.withAddress("""
                                {"items": [{"productId": %d, "quantity": 1}]}
                                """.formatted(apple))))
                .andExpect(status().isCreated());
    }
}
