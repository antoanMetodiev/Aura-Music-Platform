package com.aura.worker.adapter.worker;

import com.aura.worker.adapter.client.CatalogWorkClient;
import com.aura.worker.adapter.client.dto.DiscographyWork;
import com.aura.worker.adapter.provider.tidal.TidalProperties;
import com.aura.worker.adapter.provider.tidal.TidalRequestThrottle;
import com.aura.worker.config.DiscographyWorkerProperties;
import com.aura.worker.domain.port.MusicMetadataProvider;
import com.aura.worker.domain.port.ProviderTrack;
import com.aura.worker.domain.service.ProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pulls every track of every artist the catalog knows, one artist at a time, for as long as this
 * service is up. Claim from catalog-svc, fetch from TIDAL on <em>our</em> credentials, post back.
 *
 * <p>This loop used to live inside catalog-svc, where it spent the same TIDAL keys that served user
 * requests: opening an artist page the walk hadn't reached took two minutes, because the request had
 * to queue behind the worker's backlog and then rode out its rate-limit pauses. Moving it here did
 * not make it faster — it made it somebody else's budget.
 *
 * <p>One thread, on purpose. The provider is the bottleneck, not us, and a second thread would only
 * make it say 429 sooner.
 */
@Component
@ConditionalOnProperty(prefix = "aura.workers.discography", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DiscographyWorker {

    private static final Logger log = LoggerFactory.getLogger(DiscographyWorker.class);

    public record LastOutcome(String artist, int trackCount, int newArtists, String error, Instant at) {
    }

    private final CatalogWorkClient catalog;
    private final MusicMetadataProvider metadata;
    private final DiscographyWorkerProperties properties;
    private final TidalProperties providerProperties;
    private final TidalRequestThrottle throttle;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long artistsSinceStart;
    private volatile long tracksSinceStart;
    private volatile String inProgress;
    private final AtomicReference<LastOutcome> last = new AtomicReference<>();

    public DiscographyWorker(CatalogWorkClient catalog, MusicMetadataProvider metadata,
                             DiscographyWorkerProperties properties, TidalProperties providerProperties,
                             TidalRequestThrottle throttle) {
        this.catalog = catalog;
        this.metadata = metadata;
        this.properties = properties;
        this.providerProperties = providerProperties;
        this.throttle = throttle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!providerProperties.configured()) {
            log.warn("Discography worker not started: no TIDAL credentials (set TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET)");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("discography").start(this::loop);
        log.info("Discography worker started ({} req/s to TIDAL, {} between artists)",
                providerProperties.maxRequestsPerSecond(), properties.delayBetweenArtists());
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
                pause = step();
            } catch (CatalogWorkClient.CatalogUnavailableException e) {
                // Nothing was claimed, or the claim can't be reported on — either way, wait it out.
                last.set(new LastOutcome(inProgress, 0, 0, e.getMessage(), Instant.now()));
                log.warn("Discography worker paused {}: {}", properties.backoffOnOutage(), e.getMessage());
                pause = properties.backoffOnOutage();
            } catch (RuntimeException e) {
                last.set(new LastOutcome(inProgress, 0, 0, e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now()));
                log.error("Discography worker step failed, backing off {}", properties.backoffOnOutage(), e);
                pause = properties.backoffOnOutage();
            } finally {
                inProgress = null;
            }
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** One unit of work. Returns how long to wait before the next one. */
    private Duration step() {
        Optional<DiscographyWork.Claim> claimed = catalog.claim();
        if (claimed.isEmpty()) return properties.idleDelay();

        DiscographyWork.Claim artist = claimed.get();
        inProgress = artist.name();
        try {
            List<ProviderTrack> tracks = metadata.getArtistTracks(artist.providerResourceId());
            DiscographyWork.IngestResult result = catalog.ingest(artist.artistId(), tracks);
            artistsSinceStart++;
            tracksSinceStart += result.trackCount();
            last.set(new LastOutcome(artist.name(), result.trackCount(), result.newArtists(), null, Instant.now()));
            return properties.delayBetweenArtists();
        } catch (ProviderUnavailableException e) {
            // Not this artist's fault — give the claim back and let the provider recover.
            catalog.release(artist.artistId(), e.getMessage());
            last.set(new LastOutcome(artist.name(), 0, 0, e.getMessage(), Instant.now()));
            log.warn("TIDAL unavailable while syncing '{}', claim released, backing off {}", artist.name(), properties.backoffOnOutage());
            return properties.backoffOnOutage();
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            catalog.markFailed(artist.artistId(), error);
            last.set(new LastOutcome(artist.name(), 0, 0, error, Instant.now()));
            log.warn("Discography sync of '{}' failed", artist.name(), e);
            return properties.delayBetweenArtists();
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

    public long tracksSinceStart() {
        return tracksSinceStart;
    }

    public String inProgress() {
        return inProgress;
    }

    public LastOutcome lastOutcome() {
        return last.get();
    }

    public double currentRequestsPerSecond() {
        return throttle.currentRequestsPerSecond();
    }
}
