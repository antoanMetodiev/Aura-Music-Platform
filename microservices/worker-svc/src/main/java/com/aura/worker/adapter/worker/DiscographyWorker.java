package com.aura.worker.adapter.worker;

import com.aura.worker.adapter.client.CatalogWorkClient;
import com.aura.worker.adapter.client.dto.DiscographyWork;
import com.aura.worker.adapter.provider.tidal.TidalCallPriority;
import com.aura.worker.adapter.provider.tidal.TidalProperties;
import com.aura.worker.adapter.provider.tidal.TidalRequestThrottle;
import com.aura.worker.domain.port.MusicMetadataProvider;
import com.aura.worker.domain.port.ProviderTrack;
import com.aura.worker.domain.service.ProviderUnavailableException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pulls every track of every artist the catalog knows: claim from catalog-svc, fetch from TIDAL on
 * <em>our</em> credentials, post back.
 *
 * <p>Two of these run, and the second one is the whole point. The bulk lane works the catalog from
 * the top and takes about two minutes on a big artist. Before the split that meant somebody opening
 * an artist page waited for whatever the walk happened to be chewing on, even though their artist was
 * next in the queue — being next is no help when the current item is two minutes long. The on-demand
 * lane claims only artists someone has open, so it is idle almost always and free the moment it is
 * needed.
 *
 * <p>What it fetches is not the same thing either: catalog-svc marks such a claim {@code QUICK} and
 * the worker takes one entry per recording instead of one per release — seconds rather than minutes,
 * and all a page showing ten tracks can use. The bulk lane comes back for the rest later.
 *
 * <p>The depth comes from catalog-svc, not from the lane, so a race between the two lanes cannot
 * produce a slow fetch for a waiting page: whoever wins the claim is told the same thing.
 */
public class DiscographyWorker {

    private static final Logger log = LoggerFactory.getLogger(DiscographyWorker.class);

    public enum Lane {
        /** Only artists somebody is looking at. Idle almost always — that is what makes it fast. */
        ON_DEMAND,
        /** The walk over the whole catalog. */
        BULK
    }

    public record LastOutcome(String artist, String depth, int trackCount, int newArtists, String error, Instant at) {
    }

    private final Lane lane;
    private final Duration idleDelay;
    private final CatalogWorkClient catalog;
    private final MusicMetadataProvider metadata;
    private final com.aura.worker.config.DiscographyWorkerProperties properties;
    private final TidalProperties providerProperties;
    private final TidalRequestThrottle throttle;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long artistsSinceStart;
    private volatile long tracksSinceStart;
    private volatile String inProgress;
    private final AtomicReference<LastOutcome> last = new AtomicReference<>();

    public DiscographyWorker(Lane lane, Duration idleDelay, CatalogWorkClient catalog, MusicMetadataProvider metadata,
                             com.aura.worker.config.DiscographyWorkerProperties properties,
                             TidalProperties providerProperties, TidalRequestThrottle throttle) {
        this.lane = lane;
        this.idleDelay = idleDelay;
        this.catalog = catalog;
        this.metadata = metadata;
        this.properties = properties;
        this.providerProperties = providerProperties;
        this.throttle = throttle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!providerProperties.configured()) {
            log.warn("Discography worker [{}] not started: no TIDAL credentials (set TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET)", lane);
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("discography-" + lane.name().toLowerCase(java.util.Locale.ROOT)).start(this::loop);
        log.info("Discography worker [{}] started (polling every {})", lane, idleDelay);
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
                last.set(new LastOutcome(inProgress, null, 0, 0, e.getMessage(), Instant.now()));
                log.warn("Discography worker [{}] paused {}: {}", lane, properties.backoffOnOutage(), e.getMessage());
                pause = properties.backoffOnOutage();
            } catch (RuntimeException e) {
                last.set(new LastOutcome(inProgress, null, 0, 0, e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now()));
                log.error("Discography worker [{}] step failed, backing off {}", lane, properties.backoffOnOutage(), e);
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
        Optional<DiscographyWork.Claim> claimed = catalog.claim(lane.name());
        if (claimed.isEmpty()) return idleDelay;

        DiscographyWork.Claim artist = claimed.get();
        inProgress = artist.name();
        try {
            List<ProviderTrack> tracks = fetch(artist);
            DiscographyWork.IngestResult result = catalog.ingest(artist.artistId(), tracks, artist.depth());
            artistsSinceStart++;
            tracksSinceStart += result.trackCount();
            last.set(new LastOutcome(artist.name(), artist.depth(), result.trackCount(), result.newArtists(), null, Instant.now()));
            return properties.delayBetweenArtists();
        } catch (ProviderUnavailableException e) {
            // Not this artist's fault — give the claim back and let the provider recover.
            catalog.release(artist.artistId(), e.getMessage());
            last.set(new LastOutcome(artist.name(), artist.depth(), 0, 0, e.getMessage(), Instant.now()));
            log.warn("TIDAL unavailable while syncing '{}' [{}], claim released, backing off {}",
                    artist.name(), lane, properties.backoffOnOutage());
            return properties.backoffOnOutage();
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            catalog.markFailed(artist.artistId(), error);
            last.set(new LastOutcome(artist.name(), artist.depth(), 0, 0, error, Instant.now()));
            log.warn("Discography sync of '{}' [{}] failed", artist.name(), lane, e);
            return properties.delayBetweenArtists();
        }
    }

    /**
     * The on-demand lane's calls are marked so the shared throttle lets them past the bulk walk.
     * Both lanes spend one key, so without this the lane that exists to be fast simply queues behind
     * the one that has dozens of calls in flight.
     */
    private List<ProviderTrack> fetch(DiscographyWork.Claim artist) {
        if (lane != Lane.ON_DEMAND) {
            return metadata.getArtistTracks(artist.providerResourceId(), artist.quick());
        }
        return TidalCallPriority.runAsOnDemand(
                () -> metadata.getArtistTracks(artist.providerResourceId(), artist.quick()));
    }

    public Lane lane() {
        return lane;
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
