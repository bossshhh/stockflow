package com.stockflow.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled order housekeeping: expires PENDING orders nobody confirmed in time (so abandoned orders cannot
 * hold stock forever) and deletes idempotency keys past their TTL.
 *
 * <p>No distributed lock (ShedLock) on purpose: every action is atomic and re-checked under a row lock, so
 * when two instances run this at once, each order is expired exactly once and the slower runner simply
 * finds nothing left to do. Each order is its own transaction, so one failure (e.g. a lock timeout while
 * staff hold the row) does not undo the others; it is retried on the next run.
 */
@Component
public class OrderHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(OrderHousekeeping.class);

    /** Per run; anything beyond waits for the next run, so one run never holds the scheduler for long. */
    static final int EXPIRY_BATCH_SIZE = 200;

    private final OrderService orderService;

    public OrderHousekeeping(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(initialDelayString = "${stockflow.housekeeping.initial-delay}",
            fixedDelayString = "${stockflow.housekeeping.order-expiry-interval}")
    void scheduledExpiry() {
        expireStalePendingOrders();
    }

    @Scheduled(initialDelayString = "${stockflow.housekeeping.initial-delay}",
            fixedDelayString = "${stockflow.housekeeping.cleanup-interval}")
    void scheduledCleanup() {
        deleteExpiredIdempotencyKeys();
    }

    /** @return how many orders this run expired */
    public int expireStalePendingOrders() {
        int expired = 0;
        for (long orderId : orderService.findStalePendingOrderIds(EXPIRY_BATCH_SIZE)) {
            try {
                if (orderService.expireIfStale(orderId)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire order id={}; retrying on the next run", orderId, ex);
            }
        }
        if (expired > 0) {
            log.info("Housekeeping expired {} stale pending order(s)", expired);
        }
        return expired;
    }

    /** @return how many keys were deleted */
    public int deleteExpiredIdempotencyKeys() {
        int deleted = orderService.deleteExpiredIdempotencyKeys();
        if (deleted > 0) {
            log.info("Housekeeping deleted {} expired idempotency key(s)", deleted);
        }
        return deleted;
    }
}
