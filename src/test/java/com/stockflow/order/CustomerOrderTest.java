package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** The order state machine, tested on the entity alone: no Spring, no database. */
class CustomerOrderTest {

    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final long ACTOR = 99L;

    @Test
    void newOrderIsPendingAndTotalsItsLines() {
        CustomerOrder order = new CustomerOrder(1L, "Ada", "ada@example.com", T0);
        order.addItem(1L, "A", "Apple", new BigDecimal("1.25"), 4);
        order.addItem(2L, "B", "Banana", new BigDecimal("0.10"), 3);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("5.30");
        assertThat(order.getItems()).extracting(OrderItem::getLineTotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5.00"), new BigDecimal("0.30"));
    }

    @Test
    void confirmMovesPendingToConfirmed() {
        CustomerOrder order = pendingOrder();

        order.confirm(T0, ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(T0);
    }

    @Test
    void cannotConfirmTwiceOrConfirmCancelled() {
        CustomerOrder confirmed = pendingOrder();
        confirmed.confirm(T0, ACTOR);
        CustomerOrder cancelled = pendingOrder();
        cancelled.cancel(T0, WINDOW, ACTOR);

        assertThatThrownBy(() -> confirmed.confirm(T0, ACTOR)).isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> cancelled.confirm(T0, ACTOR)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void pendingOrderCanAlwaysBeCancelled() {
        CustomerOrder order = pendingOrder();

        order.cancel(T0.plus(Duration.ofDays(30)), WINDOW, ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedOrderCanBeCancelledAtExactlyTheEndOfTheWindow() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0, ACTOR);

        order.cancel(T0.plus(WINDOW), WINDOW, ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedOrderCannotBeCancelledOneMillisecondAfterTheWindow() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0, ACTOR);

        assertThatThrownBy(() -> order.cancel(T0.plus(WINDOW).plusMillis(1), WINDOW, ACTOR))
                .isInstanceOf(CancelWindowExpiredException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelledIsTerminal() {
        CustomerOrder order = pendingOrder();
        order.cancel(T0, WINDOW, ACTOR);

        assertThatThrownBy(() -> order.cancel(T0, WINDOW, ACTOR)).isInstanceOf(InvalidOrderStateException.class);
    }

    private static CustomerOrder pendingOrder() {
        CustomerOrder order = new CustomerOrder(1L, "Ada", "ada@example.com", T0);
        order.addItem(1L, "A", "Apple", BigDecimal.ONE, 1);
        return order;
    }

    @Test
    void fulfilmentMovesThroughEachStageInOrder() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0, ACTOR);
        order.pack(T0.plusSeconds(60), ACTOR);
        order.ship(T0.plusSeconds(120), ACTOR);
        order.deliver(T0.plusSeconds(180), ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getHistory()).extracting(OrderStatusEvent::getStatus).containsExactly(
                OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        assertThat(order.getHistory().getLast().getOccurredAt()).isEqualTo(T0.plusSeconds(180));
        assertThat(order.getHistory().getLast().getActorId()).isEqualTo(ACTOR);
    }

    @Test
    void stagesCannotBeSkipped() {
        CustomerOrder pending = pendingOrder();
        assertThatThrownBy(() -> pending.pack(T0, ACTOR)).isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> pending.ship(T0, ACTOR)).isInstanceOf(InvalidOrderStateException.class);

        CustomerOrder confirmed = pendingOrder();
        confirmed.confirm(T0, ACTOR);
        assertThatThrownBy(() -> confirmed.deliver(T0, ACTOR)).isInstanceOf(InvalidOrderStateException.class);
        assertThat(confirmed.getHistory()).hasSize(2);
    }

    @Test
    void packedShippedAndDeliveredOrdersCannotBeCancelledEvenInsideTheWindow() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0, ACTOR);
        order.pack(T0.plusSeconds(10), ACTOR);

        // Still inside the 5-minute window, but packing closes cancellation.
        assertThatThrownBy(() -> order.cancel(T0.plusSeconds(20), WINDOW, ACTOR))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PACKED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PACKED);
    }

    @Test
    void newOrderStartsItsHistoryWithTheCustomerAsActor() {
        CustomerOrder order = pendingOrder();

        assertThat(order.getHistory()).singleElement().satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(event.getActorId()).isEqualTo(1L);
        });
    }
}
