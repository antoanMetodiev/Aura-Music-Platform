package com.aura.worker.adapter.worker;

import com.aura.worker.adapter.client.RecommendationWorkClient;
import com.aura.worker.adapter.client.dto.GraphWork;
import com.aura.worker.adapter.provider.lastfm.LastFmProperties;
import com.aura.worker.adapter.provider.lastfm.LastFmRequestThrottle;
import com.aura.worker.config.GraphWorkerProperties;
import com.aura.worker.domain.port.ArtistSimilarityProvider;
import com.aura.worker.domain.port.ProviderSimilarArtist;
import com.aura.worker.domain.port.ProviderTag;
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
 * Builds the taste graph: claim an artist from recommendation-svc, ask Last.fm who their listeners
 * also like and what tags they carry, post the answer back. When nothing is left to claim it asks for
 * a seeding round instead — that one is catalog-only and costs the provider nothing.
 *
 * <p>Two provider calls per artist is the entire cost of the recommendation engine's intelligence,
 * and they are the only reason this loop is here rather than inside recommendation-svc: what moved
 * out is the credential and the pace, not the domain.
 */
@Component
@ConditionalOnProperty(prefix = "aura.workers.graph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GraphWorker {

    private static final Logger log = LoggerFactory.getLogger(GraphWorker.class);

    public record LastOutcome(String artist, int edgeCount, int tagCount, int unresolvedCount, String error, Instant at) {
    }

    private final RecommendationWorkClient recommendations;
    private final ArtistSimilarityProvider provider;
    private final GraphWorkerProperties properties;
    private final LastFmProperties providerProperties;
    private final LastFmRequestThrottle throttle;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long artistsSinceStart;
    private volatile long edgesSinceStart;
    private volatile long seedRoundsSinceStart;
    private volatile String inProgress;
    private final AtomicReference<LastOutcome> last = new AtomicReference<>();

    public GraphWorker(RecommendationWorkClient recommendations, ArtistSimilarityProvider provider,
                       GraphWorkerProperties properties, LastFmProperties providerProperties,
                       LastFmRequestThrottle throttle) {
        this.recommendations = recommendations;
        this.provider = provider;
        this.properties = properties;
        this.providerProperties = providerProperties;
        this.throttle = throttle;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!providerProperties.configured()) {
            log.warn("Graph worker not started: no Last.fm API key configured (set LASTFM_API_KEY)");
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("taste-graph").start(this::loop);
        log.info("Graph worker started ({} req/s to Last.fm, {} neighbours per artist)",
                providerProperties.maxRequestsPerSecond(), properties.similarArtistsRequested());
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
            } catch (RecommendationWorkClient.RecommendationUnavailableException e) {
                last.set(new LastOutcome(inProgress, 0, 0, 0, e.getMessage(), Instant.now()));
                log.warn("Graph worker paused {}: {}", properties.backoffOnOutage(), e.getMessage());
                pause = properties.backoffOnOutage();
            } catch (RuntimeException e) {
                last.set(new LastOutcome(inProgress, 0, 0, 0, e.getClass().getSimpleName() + ": " + e.getMessage(), Instant.now()));
                log.error("Graph worker step failed, backing off {}", properties.backoffOnOutage(), e);
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

    private Duration step() {
        Optional<GraphWork.Claim> claimed = recommendations.claim();
        if (claimed.isEmpty()) {
            // Everything known is fresh — go find artists the catalog has discovered since.
            GraphWork.SeedResult seeded = recommendations.seed();
            seedRoundsSinceStart++;
            if (seeded.endOfCatalog()) {
                log.info("Graph worker completed a full pass over the catalog, idling {}", properties.idleDelay());
                return properties.idleDelay();
            }
            return properties.delayBetweenArtists();
        }

        GraphWork.Claim artist = claimed.get();
        inProgress = artist.name();
        try {
            List<ProviderSimilarArtist> similar = provider.similarTo(artist.name(), properties.similarArtistsRequested());
            List<ProviderTag> tags = provider.topTags(artist.name());
            GraphWork.IngestResult result = recommendations.ingest(artist.artistId(), similar, tags);
            artistsSinceStart++;
            edgesSinceStart += result.edgeCount();
            last.set(new LastOutcome(artist.name(), result.edgeCount(), result.tagCount(), result.unresolvedCount(), null, Instant.now()));
            return properties.delayBetweenArtists();
        } catch (ProviderUnavailableException e) {
            // Not this artist's fault: give the claim back rather than record "no neighbours" forever.
            recommendations.release(artist.artistId(), e.getMessage());
            last.set(new LastOutcome(artist.name(), 0, 0, 0, e.getMessage(), Instant.now()));
            log.warn("Last.fm unavailable while syncing '{}', claim released, backing off {}", artist.name(), properties.backoffOnOutage());
            return properties.backoffOnOutage();
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            recommendations.markFailed(artist.artistId(), error);
            last.set(new LastOutcome(artist.name(), 0, 0, 0, error, Instant.now()));
            log.warn("Graph sync of '{}' failed", artist.name(), e);
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

    public long edgesSinceStart() {
        return edgesSinceStart;
    }

    public long seedRoundsSinceStart() {
        return seedRoundsSinceStart;
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
