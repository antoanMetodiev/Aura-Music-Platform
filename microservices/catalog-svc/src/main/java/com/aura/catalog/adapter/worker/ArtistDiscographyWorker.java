package com.aura.catalog.adapter.worker;

import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.service.ArtistDiscographyService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link ArtistDiscographyService#syncNext()} in a loop on one background (virtual) thread for
 * as long as the service is up: one artist, pause {@code delayBetweenArtists}, next artist; when
 * the queue is empty, pause {@code idleDelay} and look again. One thread on purpose — it's the
 * provider rate limiter. Disabled with {@code aura.catalog.discography-sync.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "aura.catalog.discography-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ArtistDiscographyWorker {

    private static final Logger log = LoggerFactory.getLogger(ArtistDiscographyWorker.class);

    private final ArtistDiscographyService service;
    private final DiscographySyncProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long completed;

    public ArtistDiscographyWorker(ArtistDiscographyService service, DiscographySyncProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("discography-sync").start(this::loop);
        log.info("Discography sync worker started (delay {}, idle {}, refresh after {})",
                properties.delayBetweenArtists(), properties.idleDelay(), properties.refreshAfter());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    private void loop() {
        while (running.get()) {
            Duration pause;
            try {
                var outcome = service.syncNext();
                if (outcome.isPresent()) completed++;
                // Provider down / rate-limited past what the throttle absorbed: wait it out instead
                // of burning through the queue (every artist would fail the same way).
                boolean backOff = outcome.map(o -> o.providerUnavailable()).orElse(true);
                pause = backOff ? properties.idleDelay() : properties.delayBetweenArtists();
            } catch (RuntimeException e) {
                // Whatever escaped the per-artist handling (DB down, claim failed) — don't spin.
                log.error("Discography sync tick failed, backing off {}", properties.idleDelay(), e);
                pause = properties.idleDelay();
            }
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long completed() {
        return completed;
    }
}
