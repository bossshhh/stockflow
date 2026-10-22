package com.stockflow.inventory;

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
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.ProductService;
import com.stockflow.product.dto.CreateProductRequest;

/**
 * 20 threads each try to remove 1 unit from a stock of 5 at the same instant. A read-check-write
 * implementation would let several of them see "5 on hand" and drive stock negative. The atomic conditional
 * UPDATE must let exactly 5 succeed.
 */
class InventoryConcurrencyTest extends AbstractIntegrationTest {

    private static final int THREADS = 20;
    private static final int INITIAL_STOCK = 5;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void concurrentWithdrawalsNeverDriveStockNegative() throws Exception {
        long productId = productService.create(
                new CreateProductRequest("RACE-STOCK", "Racer", null, BigDecimal.TEN)).id();
        inventoryService.adjust(productId, new StockAdjustmentRequest(INITIAL_STOCK, "Initial stock"));

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        inventoryService.adjust(productId, new StockAdjustmentRequest(-1, "Concurrent pick"));
                        return true;
                    } catch (InsufficientStockException ex) {
                        return false;
                    }
                }));
            }
            startGate.countDown();

            int succeeded = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            assertThat(succeeded).isEqualTo(INITIAL_STOCK);
            assertThat(inventoryService.getStockLevel(productId).onHand()).isZero();
            // 1 initial delivery + exactly one movement per successful withdrawal.
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM stock_movements WHERE product_id = ?", Long.class, productId))
                    .isEqualTo(INITIAL_STOCK + 1L);
        } finally {
            executor.shutdownNow();
        }
    }
}
