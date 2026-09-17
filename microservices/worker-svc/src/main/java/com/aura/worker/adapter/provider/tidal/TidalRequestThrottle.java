package com.aura.worker.adapter.provider.tidal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide pacing of this service's TIDAL calls: evenly spaced starts, at most
 * {@code maxConcurrentRequests} in flight, and a full stop for the {@code Retry-After} period
 * whenever TIDAL says 429.
 *
 * <p>Two classes of caller, because the two discography lanes share one key and therefore one budget.
 * An <em>on-demand</em> call (someone is looking at that artist's page right now, see
 * {@link TidalCallPriority}) always goes first; a <em>bulk</em> call only takes a slot while no
 * on-demand call is waiting. Without that, the on-demand lane queues behind the bulk lane's dozens of
 * back-to-back calls and a six-call fetch takes a minute and a half — measured, before this was here.
 *
 * <p>TIDAL doesn't publish its limit or send rate-limit headers, so the spacing is adaptive (AIMD):
 * every 429 doubles it, every success shrinks it a little back toward the configured
 * {@code maxRequestsPerSecond}.
 */
@Component
public class TidalRequestThrottle {

    private static final Logger log = LoggerFactory.getLogger(TidalRequestThrottle.class);
    private static final long MAX_SPACING_NANOS = Duration.ofSeconds(5).toNanos();
    private static final long ON_DEMAND_YIELD_MS = 25;

    private final long baseSpacingNanos;
    private final Semaphore inFlight;
    private final AtomicInteger onDemandWaiting = new AtomicInteger();
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
        boolean onDemand = TidalCallPriority.isOnDemand();
        if (onDemand) onDemandWaiting.incrementAndGet();
        try {
            // Yield only *before* taking a permit — holding one while waiting would starve the very
            // call we are yielding to.
            if (!onDemand) yieldToOnDemand();
            inFlight.acquire();
            try {
                long waitNanos;
                synchronized (this) {
                    long now = System.nanoTime();
                    long slot = Math.max(Math.max(nextSlotNanos, pausedUntilNanos), now);
                    nextSlotNanos = slot + spacingNanos;
                    waitNanos = slot - now;
                }
                if (waitNanos > 0) Thread.sleep(Duration.ofNanos(waitNanos));
            } catch (InterruptedException | RuntimeException e) {
                inFlight.release();
                throw e;
            }
        } finally {
            if (onDemand) onDemandWaiting.decrementAndGet();
        }
    }

    /** The bulk walk steps aside for as long as any on-demand call is queued. */
    private void yieldToOnDemand() throws InterruptedException {
        while (onDemandWaiting.get() > 0) {
            Thread.sleep(ON_DEMAND_YIELD_MS);
        }
    }

    public int onDemandWaiting() {
        return onDemandWaiting.get();
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

    public synchronized double currentRequestsPerSecond() {
        return 1_000_000_000.0 / spacingNanos;
    }
}
