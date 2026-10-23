package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.inventory.InsufficientStockException;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.user.CurrentUser;

/** The races the project exists to win, run against real Postgres with real threads. */
class OrderConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private OrderService orderService;

    private CurrentUser racer;

    @BeforeEach
    void registerRacer() {
        racer = asCustomer(createCustomer("racer@example.com"));
    }

    @Test
    void manyCustomersRaceForTheLastUnitAndExactlyOneGetsIt() throws Exception {
        long lastUnit = productWithStock("LAST-1", "50.00", 1);

        List<Outcome> outcomes = runConcurrently(THREADS, i -> () -> {
            try {
                orderService.create(racer, order(new OrderItemRequest(lastUnit, 1)));
                return Outcome.SUCCEEDED;
            } catch (InsufficientStockException ex) {
                return Outcome.REJECTED;
            }
        });

        assertThat(outcomes).containsOnlyOnce(Outcome.SUCCEEDED);
        assertThat(reserved(lastUnit)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentConfirmsOfOneOrderDeductStockOnlyOnce() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 10);
        long orderId = orderService.create(racer, order(new OrderItemRequest(apple, 3))).id();

        List<Outcome> outcomes = runConcurrently(THREADS, i -> () -> {
            try {
                orderService.confirm(STAFF_ACTOR, orderId);
                return Outcome.SUCCEEDED;
            } catch (InvalidOrderStateException ex) {
                return Outcome.REJECTED;
            }
        });

        assertThat(outcomes).containsOnlyOnce(Outcome.SUCCEEDED);
        assertThat(onHand(apple)).isEqualTo(7);
        assertThat(reserved(apple)).isZero();
    }

    @Test
    void ordersLockingTheSameProductsInOppositeRequestOrderDoNotDeadlock() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 100);
        long pear = productWithStock("PEAR", "1.00", 100);
        int threads = 20;

        // Half the requests list [apple, pear], half [pear, apple]. Without sorting by product id, Postgres
        // would detect deadlocks and abort some of these transactions.
        List<Outcome> outcomes = runConcurrently(threads, i -> () -> {
            OrderItemRequest a = new OrderItemRequest(apple, 1);
            OrderItemRequest p = new OrderItemRequest(pear, 1);
            orderService.create(racer, i % 2 == 0 ? order(a, p) : order(p, a));
            return Outcome.SUCCEEDED;
        });

        assertThat(outcomes).hasSize(threads).containsOnly(Outcome.SUCCEEDED);
        assertThat(reserved(apple)).isEqualTo(threads);
        assertThat(reserved(pear)).isEqualTo(threads);
    }

    @Test
    void sameIdempotencyKeyFromManyThreadsPlacesExactlyOneOrder() throws Exception {
        long apple = productWithStock("APPLE", "1.00", 100);
        Set<Long> orderIds = ConcurrentHashMap.newKeySet();

        // A double click or retry storm: the same request with the same key, all at the same instant.
        List<Outcome> outcomes = runConcurrently(THREADS, i -> () -> {
            OrderPlacement placement = orderService.place(racer, order(new OrderItemRequest(apple, 3)), "retry-storm-key");
            orderIds.add(placement.order().id());
            return placement.replayed() ? Outcome.REPLAYED : Outcome.SUCCEEDED;
        });

        assertThat(outcomes).hasSize(THREADS).containsOnlyOnce(Outcome.SUCCEEDED);
        assertThat(outcomes).filteredOn(Outcome.REPLAYED::equals).hasSize(THREADS - 1);
        assertThat(orderIds).hasSize(1);
        assertThat(reserved(apple)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Long.class)).isEqualTo(1L);
    }

    private static CreateOrderRequest order(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    /** Starts all tasks at the same instant (start gate) and collects their results. */
    private static List<Outcome> runConcurrently(int threads, IntFunction<Callable<Outcome>> taskFactory)
            throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Outcome> task = taskFactory.apply(i);
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private enum Outcome { SUCCEEDED, REJECTED, REPLAYED }
}
