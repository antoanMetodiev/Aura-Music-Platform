package com.aura.recommendation.adapter.worker;

import com.aura.recommendation.adapter.provider.lastfm.LastFmProperties;
import com.aura.recommendation.config.GraphSyncProperties;
import com.aura.recommendation.domain.port.GraphSyncStore;
import com.aura.recommendation.domain.service.ArtistGraphService;
import com.aura.recommendation.domain.service.CatalogUnavailableException;
import com.aura.recommendation.domain.service.ProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds the taste graph continuously on one background (virtual) thread: sync the next artist due,
 * and when every known artist is fresh, pull another page of artists from the catalog. One thread on
 * purpose — the provider allows a handful of requests a second and those requests are the whole cost
 * of this service, so there is nothing to gain from parallelism and a ban to lose.
 *
 * <p>Disabled with {@code aura.recommendations.graph-sync.enabled=false}, and it never starts at all
 * without a Last.fm key — there would be nothing to fetch.
 */
@Component
@ConditionalOnProperty(prefix = "aura.recommendations.graph-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ArtistGraphWorker {

    private static final Logger log = LoggerFactory.getLogger(ArtistGraphWorker.class);

    private final ArtistGraphService service;
    private final GraphSyncProperties properties;
    private final LastFmProperties providerProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long artistsSinceStart;
    private volatile long edgesSinceStart;
    private volatile long pagesSeededSinceStart;
    private volatile String lastError;

    public ArtistGraphWorker(ArtistGraphService service, GraphSyncProperties properties, LastFmProperties providerProperties) {
        this.service = service;
        this.properties = properties;
        this.providerProperties = providerProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!providerProperties.configured()) {
            log.warn("Graph worker not started: no Last.fm API key configured (set LASTFM_API_KEY)");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("artist-graph").start(this::loop);
        log.info("Artist graph worker started (pace {}/s, refresh after {}, {} neighbours per artist)",
                providerProperties.maxRequestsPerSecond(), properties.refreshAfter(), properties.similarArtistsRequested());
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
                Optional<GraphSyncStore.SyncOutcome> outcome = service.syncNext();
                if (outcome.isPresent()) {
                    artistsSinceStart++;
                    edgesSinceStart += outcome.get().edgeCount();
                    lastError = outcome.get().error();
                    pause = properties.delayBetweenArtists();
                } else {
                    // Everything known is fresh — go find artists the catalog has discovered since.
                    ArtistGraphService.SeedOutcome seeded = service.seedNextPage(properties.seedPageSize());
                    pagesSeededSinceStart++;
                    lastError = null;
                    if (seeded.endOfCatalog()) {
                        log.info("Artist graph worker completed a full pass ({}), idling {}",
                                seeded.cursor().passes(), properties.idleDelay());
                        pause = properties.idleDelay();
                    } else {
                        // Seeding is catalog-only; a page that added nothing new should move on quickly.
                        pause = properties.delayBetweenArtists();
                    }
                }
            } catch (ProviderUnavailableException | CatalogUnavailableException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Artist graph worker paused {}: {}", properties.backoffOnOutage(), lastError);
                pause = properties.backoffOnOutage();
            } catch (RuntimeException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("Artist graph worker step failed, backing off {}", properties.backoffOnOutage(), e);
                pause = properties.backoffOnOutage();
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

    public long artistsSinceStart() {
        return artistsSinceStart;
    }

    public long edgesSinceStart() {
        return edgesSinceStart;
    }

    public long pagesSeededSinceStart() {
        return pagesSeededSinceStart;
    }

    public String lastError() {
        return lastError;
    }
}
