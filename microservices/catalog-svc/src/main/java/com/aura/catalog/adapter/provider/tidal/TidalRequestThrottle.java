package com.aura.catalog.adapter.provider.tidal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * Process-wide pacing of TIDAL calls: evenly spaced starts, at most {@code maxConcurrentRequests}
 * in flight, and a full stop for the {@code Retry-After} period whenever TIDAL says 429. Callers
 * block (virtual threads, so cheaply) until their slot comes up.
 *
 * <p>TIDAL doesn't publish its limit or send rate-limit headers, so the spacing is adaptive
 * (AIMD): every 429 doubles it, every success shrinks it a little back toward the configured
 * {@code maxRequestsPerSecond}. The wire sees a steady stream that settles just under whatever the
 * real limit is.
 */
@Component
public class TidalRequestThrottle {

    private static final Logger log = LoggerFactory.getLogger(TidalRequestThrottle.class);
    private static final long MAX_SPACING_NANOS = Duration.ofSeconds(5).toNanos();

    private final long baseSpacingNanos;
    private final Semaphore inFlight;
    private long spacingNanos;
    private long nextSlotNanos = System.nanoTime();
    private volatile long pausedUntilNanos = System.nanoTime();

    public TidalRequestThrottle(TidalProperties properties) {
        this.baseSpacingNanos = 1_000_000_000L / Math.max(1, properties.maxRequestsPerSecond());
        this.spacingNanos = baseSpacingNanos;
        this.inFlight = new Semaphore(Math.max(1, properties.maxConcurrentRequests()), true);
    }

    /** Blocks until this call may go out. Every acquire must be paired with {@link #release()}. */
    public void acquire() throws InterruptedException {
        inFlight.acquire();
        try {
            long waitNanos;
            synchronized (this) {
                long now = System.nanoTime();
                long earliest = Math.max(nextSlotNanos, pausedUntilNanos);
                long slot = Math.max(earliest, now);
                nextSlotNanos = slot + spacingNanos;
                waitNanos = slot - now;
            }
            if (waitNanos > 0) Thread.sleep(Duration.ofNanos(waitNanos));
        } catch (InterruptedException | RuntimeException e) {
            inFlight.release();
            throw e;
        }
    }

    public void release() {
        inFlight.release();
    }

    /** A call came back fine — creep back toward the configured rate. */
    public synchronized void succeeded() {
        spacingNanos = Math.max(baseSpacingNanos, (long) (spacingNanos * 0.97));
    }

    /** TIDAL asked us to back off: nothing goes out for {@code retryAfter}, and calls are spaced twice as far apart afterwards. */
    public synchronized void rateLimited(Duration retryAfter) {
        long until = System.nanoTime() + retryAfter.toNanos();
        if (until > pausedUntilNanos) pausedUntilNanos = until;
        spacingNanos = Math.min(MAX_SPACING_NANOS, spacingNanos * 2);
        log.info("TIDAL throttle: pausing {} and spacing calls {} ms apart", retryAfter, spacingNanos / 1_000_000);
    }

    public double currentRequestsPerSecond() {
        return 1_000_000_000.0 / spacingNanos;
    }
}
