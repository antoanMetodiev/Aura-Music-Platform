package com.aura.catalog.domain.service;

import com.aura.catalog.config.DiscographySyncProperties;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.DiscographySyncStore;
import com.aura.catalog.domain.port.DiscographySyncStore.PendingArtist;
import com.aura.catalog.domain.port.MusicMetadataProvider;
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.port.UpsertedBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Continuous discovery: take the next artist in our catalog whose discography we don't have (or
 * have had for longer than {@code refreshAfter}), pull every track they appear on from the
 * provider, persist all of it. Every track brings its album and its featured/album artists along,
 * and any of those artists we didn't know yet is inserted with no discography — which puts it in
 * the queue for a later pass. The catalog therefore grows on its own from whatever seeds it has
 * (searches, opened albums, anything already in {@code catalog.artists}).
 */
@Service
public class ArtistDiscographyService {

    private static final Logger log = LoggerFactory.getLogger(ArtistDiscographyService.class);

    public record Outcome(PendingArtist artist, int trackCount, int newArtists, String error, boolean providerUnavailable, Instant at) {
        public boolean succeeded() {
            return error == null;
        }
    }

    private final DiscographySyncStore queue;
    private final CatalogStore store;
    private final MusicMetadataProvider metadata;
    private final DiscographySyncProperties properties;
    private final ArtistMergeService artistMerge;
    private final AtomicReference<Outcome> last = new AtomicReference<>();
    private final AtomicReference<PendingArtist> inProgress = new AtomicReference<>();

    public ArtistDiscographyService(DiscographySyncStore queue, CatalogStore store,
                                    MusicMetadataProvider metadata, DiscographySyncProperties properties,
                                    ArtistMergeService artistMerge) {
        this.artistMerge = artistMerge;
        this.queue = queue;
        this.store = store;
        this.metadata = metadata;
        this.properties = properties;
    }

    /** One unit of work: claim → fetch → persist → mark. Empty when there was nothing to claim. */
    public Optional<Outcome> syncNext() {
        Optional<PendingArtist> claimed = queue.claimNext(properties.refreshAfter(), properties.retryAfter());
        if (claimed.isEmpty()) return Optional.empty();
        PendingArtist artist = claimed.get();
        inProgress.set(artist);
        try {
            long artistsBefore = queue.stats(properties.refreshAfter()).artistsTotal();
            List<ProviderTrack> tracks = metadata.getArtistTracks(artist.ref().providerResourceId());
            UpsertedBatch persisted = store.upsertBatch(tracks, List.of(), List.of());
            queue.markSynced(artist.id(), persisted.tracks().size());
            // The tracks just stored are the evidence that same-named profiles are one act (V14).
            artistMerge.mergeDuplicatesNamed(artist.name());
            long newArtists = queue.stats(properties.refreshAfter()).artistsTotal() - artistsBefore;
            Outcome outcome = new Outcome(artist, persisted.tracks().size(), (int) newArtists, null, false, Instant.now());
            log.info("Discography sync: '{}' -> {} tracks, {} new artists discovered", artist.name(), outcome.trackCount(), newArtists);
            last.set(outcome);
            return Optional.of(outcome);
        } catch (RuntimeException e) {
            Throwable root = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
            String reason = root.getClass().getSimpleName() + ": " + root.getMessage();
            if (root instanceof ProviderUnavailableException) {
                // Nothing is known about the artist — put it back untouched; the worker backs off.
                queue.release(artist.id());
                log.warn("Discography sync of '{}' postponed, provider unavailable: {}", artist.name(), reason);
                Outcome outcome = new Outcome(artist, 0, 0, reason, true, Instant.now());
                last.set(outcome);
                return Optional.of(outcome);
            }
            queue.markFailed(artist.id(), reason);
            Outcome outcome = new Outcome(artist, 0, 0, reason, false, Instant.now());
            log.warn("Discography sync of '{}' failed, will retry after {}: {}", artist.name(), properties.retryAfter(), reason);
            last.set(outcome);
            return Optional.of(outcome);
        } finally {
            inProgress.set(null);
        }
    }

    public DiscographySyncStore.SyncStats stats() {
        return queue.stats(properties.refreshAfter());
    }

    public List<DiscographySyncStore.RecentSync> recent(int limit) {
        return queue.recent(limit);
    }

    public Optional<Outcome> lastOutcome() {
        return Optional.ofNullable(last.get());
    }

    public Optional<PendingArtist> inProgress() {
        return Optional.ofNullable(inProgress.get());
    }
}
