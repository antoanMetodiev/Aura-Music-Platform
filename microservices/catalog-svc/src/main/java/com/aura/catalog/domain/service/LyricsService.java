package com.aura.catalog.domain.service;

import com.aura.catalog.config.LyricsProperties;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Lyrics;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.LyricsProvider;
import com.aura.catalog.domain.port.LyricsQuery;
import com.aura.catalog.domain.port.LyricsStore;
import com.aura.catalog.domain.port.ProviderLyrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lyrics for a track (todo.md §2.4): cache → provider → cache.
 *
 * <pre>
 *   read ──► track_lyrics ──(found)──────────────────► return
 *               │
 *               ├─(miss, recorded recently)──────────► LYRICS_NOT_FOUND
 *               │
 *               └─(never asked / miss expired)──► provider ──(text)──► save ──► return
 *                                                     │
 *                                                     ├─(nothing)──► save miss ──► LYRICS_NOT_FOUND
 *                                                     └─(outage)───► PROVIDER_UNAVAILABLE (nothing written)
 * </pre>
 *
 * Concurrent first requests for the same track (a room full of friends pressing play on the same song)
 * collapse into one provider round-trip via {@link #pending}, the way {@code CatalogService} coalesces
 * search refreshes.
 */
@Service
public class LyricsService {

    private static final Logger log = LoggerFactory.getLogger(LyricsService.class);

    private final LyricsStore store;
    private final LyricsProvider provider;
    private final CatalogService catalog;
    private final LyricsProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, CompletableFuture<Optional<Lyrics>>> pending = new ConcurrentHashMap<>();

    public LyricsService(LyricsStore store, LyricsProvider provider, CatalogService catalog,
                         LyricsProperties properties, Clock clock) {
        this.store = store;
        this.provider = provider;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @throws CatalogEntityNotFoundException {@code TRACK_NOT_FOUND} for an unknown track,
     *                                        {@code LYRICS_NOT_FOUND} when no provider has text for it
     * @throws ProviderUnavailableException   the provider is down — the caller may retry later; nothing was cached
     */
    public Lyrics getLyrics(UUID trackId) {
        Optional<LyricsStore.Cached> cached = store.find(trackId);
        if (cached.isPresent()) {
            LyricsStore.Cached hit = cached.get();
            if (!hit.isMiss()) return hit.lyrics();
            if (!isExpired(hit.fetchedAt())) throw notFound(trackId);
        }
        return coalescedLookup(trackId).orElseThrow(() -> notFound(trackId));
    }

    private Optional<Lyrics> coalescedLookup(UUID trackId) {
        CompletableFuture<Optional<Lyrics>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<Lyrics>> existing = pending.putIfAbsent(trackId, mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }
        try {
            Optional<Lyrics> result = lookup(trackId);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            pending.remove(trackId, mine);
        }
    }

    private Optional<Lyrics> lookup(UUID trackId) {
        Track track = catalog.getTrack(trackId);
        Instant now = clock.instant();
        Optional<ProviderLyrics> found = provider.find(toQuery(track));
        if (found.isEmpty()) {
            store.saveMiss(trackId, now);
            log.debug("No lyrics for track {} ('{}')", trackId, track.title());
            return Optional.empty();
        }
        ProviderLyrics p = found.get();
        Lyrics lyrics = new Lyrics(trackId, provider.provider(), p.instrumental(), p.synced(), p.plain(), now);
        store.save(lyrics);
        return Optional.of(lyrics);
    }

    private static LyricsQuery toQuery(Track track) {
        Artist primary = track.primaryArtist();
        return new LyricsQuery(
                primary == null ? "" : primary.name(),
                track.title(),
                track.album() == null ? null : track.album().title(),
                track.durationMs(),
                track.isrc()
        );
    }

    private boolean isExpired(Instant fetchedAt) {
        return fetchedAt.plus(properties.retryMissingAfter()).isBefore(clock.instant());
    }

    private static CatalogEntityNotFoundException notFound(UUID trackId) {
        return new CatalogEntityNotFoundException("Lyrics", "for track " + trackId);
    }
}
