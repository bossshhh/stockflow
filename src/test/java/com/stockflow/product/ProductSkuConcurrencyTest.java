package com.stockflow.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.stockflow.AbstractIntegrationTest;
import com.stockflow.product.dto.CreateProductRequest;

/**
 * Many threads create the same SKU at the same instant. The service's exists-check cannot stop this on its
 * own (they all see "not taken"); the database UNIQUE constraint must, and the service must turn the
 * losers' failures into DuplicateSkuException rather than a 500.
 */
class ProductSkuConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private ProductService productService;

    @Test
    void exactlyOneConcurrentCreateWinsAndTheRestGetDuplicateSku() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        productService.create(new CreateProductRequest("RACE-1", "Racer", null, BigDecimal.TEN));
                        return Outcome.CREATED;
                    } catch (DuplicateSkuException ex) {
                        return Outcome.DUPLICATE;
                    }
                }));
            }
            startGate.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).containsOnlyOnce(Outcome.CREATED);
            assertThat(outcomes).filteredOn(Outcome.DUPLICATE::equals).hasSize(THREADS - 1);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM products WHERE sku = 'RACE-1'", Long.class))
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private enum Outcome { CREATED, DUPLICATE }
}
