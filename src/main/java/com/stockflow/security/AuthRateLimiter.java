package com.stockflow.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stockflow.common.exception.TooManyRequestsException;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/**
 * Token buckets that slow down password guessing and scripted sign-ups:
 * <ul>
 *   <li>failed logins per email: guessing one account's password;</li>
 *   <li>failed logins per IP: one machine trying many accounts (credential stuffing);</li>
 *   <li>registrations per IP: mass account creation.</li>
 * </ul>
 * Logins take a token up front (before BCrypt runs) and a successful login gives it back, so only failures
 * count. Unknown emails get buckets like real ones: a 429 never reveals whether an account exists.
 *
 * <p>Buckets live in memory, in size-capped caches, so random emails cannot exhaust memory. That is right
 * for a single instance; several instances need a shared store (Bucket4j supports PostgreSQL and Redis).
 * Time comes from the injected {@link Clock}, so tests can move it forward.
 */
@Component
public class AuthRateLimiter {

    /** Upper bound per cache. Evicting a bucket only forgets that key's past attempts. */
    private static final long MAX_TRACKED_KEYS = 100_000;
    private static final String TRY_AGAIN = "Too many attempts; try again in ";

    /** Refunds the tokens of a login attempt; call it when the login succeeded. */
    @FunctionalInterface
    public interface LoginAttempt {
        void succeeded();
    }

    private final RateLimit failedLoginsPerEmail;
    private final RateLimit failedLoginsPerIp;
    private final RateLimit registrationsPerIp;

    public AuthRateLimiter(SecurityProperties properties, Clock clock) {
        SecurityProperties.RateLimitSettings settings = properties.rateLimit();
        TimeMeter time = new ClockTimeMeter(clock);
        this.failedLoginsPerEmail = new RateLimit(settings.failedLoginsPerEmail(), time);
        this.failedLoginsPerIp = new RateLimit(settings.failedLoginsPerIp(), time);
        this.registrationsPerIp = new RateLimit(settings.registrationsPerIp(), time);
    }

    /**
     * Takes one attempt from both the IP's and the email's budget, or throws if either is used up.
     *
     * @param normalizedEmail the email exactly as accounts are keyed (trimmed, lower-case)
     * @throws TooManyRequestsException with the time until the next attempt is allowed
     */
    public LoginAttempt beginLogin(String clientIp, String normalizedEmail) {
        Bucket ipBucket = failedLoginsPerIp.bucketFor(clientIp);
        ConsumptionProbe ip = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ip.isConsumed()) {
            throw tooMany(ip);
        }
        Bucket emailBucket = failedLoginsPerEmail.bucketFor(normalizedEmail);
        ConsumptionProbe email = emailBucket.tryConsumeAndReturnRemaining(1);
        if (!email.isConsumed()) {
            ipBucket.addTokens(1);
            throw tooMany(email);
        }
        return () -> {
            ipBucket.addTokens(1);
            emailBucket.addTokens(1);
        };
    }

    /** @throws TooManyRequestsException when this IP registered too many accounts recently */
    public void registration(String clientIp) {
        ConsumptionProbe probe = registrationsPerIp.bucketFor(clientIp).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw tooMany(probe);
        }
    }

    private static TooManyRequestsException tooMany(ConsumptionProbe probe) {
        // Round up: telling a client to come back a fraction of a second early only earns another 429.
        long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
        Duration wait = Duration.ofSeconds(seconds);
        return new TooManyRequestsException(TRY_AGAIN + describe(wait), wait);
    }

    private static String describe(Duration wait) {
        long minutes = (wait.toSeconds() + 59) / 60;
        return wait.toSeconds() < 60 ? wait.toSeconds() + " seconds" : minutes + (minutes == 1 ? " minute" : " minutes");
    }

    /** One kind of limit: a bucket per key, all with the same capacity and refill. */
    private static final class RateLimit {

        private final SecurityProperties.Limit limit;
        private final TimeMeter time;
        private final Cache<String, Bucket> buckets;

        RateLimit(SecurityProperties.Limit limit, TimeMeter time) {
            this.limit = limit;
            this.time = time;
            // An untouched bucket is full again after one period, so forgetting it then loses nothing.
            this.buckets = Caffeine.newBuilder()
                    .maximumSize(MAX_TRACKED_KEYS)
                    .expireAfterAccess(limit.per())
                    .build();
        }

        Bucket bucketFor(String key) {
            return buckets.get(key, ignored -> Bucket.builder()
                    .addLimit(bandwidth -> bandwidth.capacity(limit.attempts()).refillGreedy(limit.attempts(), limit.per()))
                    .withCustomTimePrecision(time)
                    .build());
        }
    }

    /** Bucket4j reads time through this, so it follows our (test-controllable) Clock. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
