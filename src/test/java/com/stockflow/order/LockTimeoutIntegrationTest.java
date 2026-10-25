package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.stockflow.AbstractIntegrationTest;
import com.stockflow.common.web.ApiHeaders;

/**
 * A request that needs a row someone else has locked must give up after the lock timeout (500 ms in the
 * test profile) with a clean 409, instead of waiting forever and tying up a pooled connection.
 */
class LockTimeoutIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    /** Guard for the request itself: without a lock timeout it would wait forever. */
    private static final Duration REQUEST_GUARD = Duration.ofSeconds(10);

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private MockMvc customerMvc;
    private long customerId;
    private long apple;

    @BeforeEach
    void setUp() {
        customerId = createCustomer("ada@example.com");
        customerMvc = mvcAs(customer(customerId));
        apple = productWithStock("APPLE", "1.00", 10);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void everyPooledConnectionHasTheLockTimeout() {
        assertThat(jdbcTemplate.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("500ms");
    }

    @Test
    void placingAnOrderWhileItsStockRowIsLockedFailsFastAndChangesNothing() throws Exception {
        try (LockHolder ignored = holdLock(jdbc -> jdbc.queryForList(
                "SELECT * FROM inventory WHERE product_id = ? FOR UPDATE", apple))) {
            long started = System.nanoTime();
            perform(customerMvc, orderRequest(null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(
                    Duration.ofMillis(400), Duration.ofSeconds(5));
        }

        assertThat(reserved(apple)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customer_orders", Long.class)).isZero();
        perform(customerMvc, orderRequest(null)).andExpect(status().isCreated());
    }

    @Test
    void confirmingAnOrderWhoseRowIsLockedFailsFastAndCanBeRetried() throws Exception {
        long orderId = ((Number) JsonPath.read(perform(customerMvc, orderRequest(null))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        MockMvc staffMvc = mvcAs(staff());

        try (LockHolder ignored = holdLock(jdbc -> jdbc.queryForList(
                "SELECT * FROM customer_orders WHERE id = ? FOR UPDATE", orderId))) {
            perform(staffMvc, post(ORDERS + "/" + orderId + "/confirm"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        }

        perform(staffMvc, post(ORDERS + "/" + orderId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void retryWhileTheFirstAttemptStillHoldsTheKeyFailsFastAndTheKeyStaysUsable() throws Exception {
        String key = "slow-first-attempt";
        // The "first attempt" has claimed the key but not committed yet (and will roll back).
        try (LockHolder ignored = holdLock(jdbc -> jdbc.update("""
                INSERT INTO order_idempotency_keys (customer_id, idempotency_key, request_hash, created_at)
                VALUES (?, ?, 'pending', now())
                """, customerId, key))) {
            perform(customerMvc, orderRequest(key))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        }

        perform(customerMvc, orderRequest(key)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder orderRequest(String idempotencyKey) {
        MockHttpServletRequestBuilder request = post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                .content(TestAddresses.withAddress("""
                        {"items": [{"productId": %d, "quantity": 2}]}
                        """.formatted(apple)));
        return idempotencyKey == null ? request : request.header(ApiHeaders.IDEMPOTENCY_KEY, idempotencyKey);
    }

    /** Runs the request on another thread, so a missing lock timeout fails the test instead of hanging it. */
    private ResultActions perform(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return executor.submit(() -> mvc.perform(request)).get(REQUEST_GUARD.toSeconds(), TimeUnit.SECONDS);
    }

    /** Another user's transaction: runs {@code work} (which takes row locks) and holds them until closed. */
    private LockHolder holdLock(Consumer<JdbcTemplate> work) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
            work.accept(jdbcTemplate);
            locked.countDown();
            awaitQuietly(release);
            status.setRollbackOnly();
        }));
        if (!locked.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("The lock holder could not take its locks");
        }
        return () -> {
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        };
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface LockHolder extends AutoCloseable {
        @Override
        void close() throws Exception;
    }
}
