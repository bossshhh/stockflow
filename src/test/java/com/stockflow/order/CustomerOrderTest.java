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

    @Test
    void newOrderIsPendingAndTotalsItsLines() {
        CustomerOrder order = new CustomerOrder("Ada", "ada@example.com", T0);
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

        order.confirm(T0);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(T0);
    }

    @Test
    void cannotConfirmTwiceOrConfirmCancelled() {
        CustomerOrder confirmed = pendingOrder();
        confirmed.confirm(T0);
        CustomerOrder cancelled = pendingOrder();
        cancelled.cancel(T0, WINDOW);

        assertThatThrownBy(() -> confirmed.confirm(T0)).isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> cancelled.confirm(T0)).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void pendingOrderCanAlwaysBeCancelled() {
        CustomerOrder order = pendingOrder();

        order.cancel(T0.plus(Duration.ofDays(30)), WINDOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedOrderCanBeCancelledAtExactlyTheEndOfTheWindow() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0);

        order.cancel(T0.plus(WINDOW), WINDOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedOrderCannotBeCancelledOneMillisecondAfterTheWindow() {
        CustomerOrder order = pendingOrder();
        order.confirm(T0);

        assertThatThrownBy(() -> order.cancel(T0.plus(WINDOW).plusMillis(1), WINDOW))
                .isInstanceOf(CancelWindowExpiredException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelledIsTerminal() {
        CustomerOrder order = pendingOrder();
        order.cancel(T0, WINDOW);

        assertThatThrownBy(() -> order.cancel(T0, WINDOW)).isInstanceOf(InvalidOrderStateException.class);
    }

    private static CustomerOrder pendingOrder() {
        CustomerOrder order = new CustomerOrder("Ada", "ada@example.com", T0);
        order.addItem(1L, "A", "Apple", BigDecimal.ONE, 1);
        return order;
    }
}
