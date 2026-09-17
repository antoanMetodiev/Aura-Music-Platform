package com.aura.worker.adapter.provider.lastfm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Evenly spaced Last.fm calls, process-wide. Simpler than catalog-svc's TIDAL throttle on purpose:
 * every call here comes from one background worker thread, so there is no interactive traffic to
 * yield to and no concurrency to cap — only a pace to keep, and a full stop when they say 429.
 */
@Component
public class LastFmRequestThrottle {

    private static final Logger log = LoggerFactory.getLogger(LastFmRequestThrottle.class);
    private static final long MAX_SPACING_NANOS = Duration.ofSeconds(10).toNanos();

    private final long baseSpacingNanos;
    private long spacingNanos;
    private long nextSlotNanos = System.nanoTime();
    private long pausedUntilNanos = System.nanoTime();

    public LastFmRequestThrottle(LastFmProperties properties) {
        this.baseSpacingNanos = 1_000_000_000L / Math.max(1, properties.maxRequestsPerSecond());
        this.spacingNanos = baseSpacingNanos;
    }

    /** Blocks until this call may go out. */
    public void acquire() throws InterruptedException {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(Math.max(nextSlotNanos, pausedUntilNanos), now);
            nextSlotNanos = slot + spacingNanos;
            waitNanos = slot - now;
        }
        if (waitNanos > 0) Thread.sleep(Duration.ofNanos(waitNanos));
    }

    /** A call came back fine — creep back toward the configured rate. */
    public synchronized void succeeded() {
        spacingNanos = Math.max(baseSpacingNanos, (long) (spacingNanos * 0.95));
    }

    /** Rate limited: nothing goes out for {@code pause}, and calls are spaced twice as far apart afterwards. */
    public synchronized void rateLimited(Duration pause) {
        long until = System.nanoTime() + pause.toNanos();
        if (until > pausedUntilNanos) pausedUntilNanos = until;
        spacingNanos = Math.min(MAX_SPACING_NANOS, spacingNanos * 2);
        log.info("Last.fm throttle: pausing {} and spacing calls {} ms apart", pause, spacingNanos / 1_000_000);
    }

    public synchronized double currentRequestsPerSecond() {
        return 1_000_000_000.0 / spacingNanos;
    }
}
