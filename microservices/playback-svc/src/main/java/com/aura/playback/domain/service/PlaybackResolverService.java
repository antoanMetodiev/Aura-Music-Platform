package com.aura.playback.domain.service;

import com.aura.playback.config.MatchingProperties;
import com.aura.playback.config.ResolverProperties;
import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.MatchMethod;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.PlaybackSource;
import com.aura.playback.domain.model.ScoredCandidate;
import com.aura.playback.domain.model.VideoCandidate;
import com.aura.playback.domain.port.CatalogTrackLookup;
import com.aura.playback.domain.port.PlaybackSourceStore;
import com.aura.playback.domain.port.VideoSearchProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Кой външен playback source най-вероятно представлява този track?" (Project-Info.md §16) —
 * never decides what the song IS, only whether a YouTube video is confidently the same recording.
 *
 * <p>Quota safety (§20): a track already resolved (in any state — matched, candidate, or a known
 * miss) never triggers a second YouTube search; only a never-before-seen track does. Concurrent
 * requests for the same never-before-seen track collapse into one search via {@link #pending} —
 * 100 simultaneous requests for the same unresolved song cost one YouTube search, not 100.
 */
@Service
public class PlaybackResolverService {

    private final PlaybackSourceStore store;
    private final CatalogTrackLookup catalogLookup;
    private final VideoSearchProvider videoSearchProvider;
    private final TrackMatcher matcher;
    private final MatchingProperties thresholds;
    private final ResolverProperties resolverProperties;
    private final Clock clock;

    private final ConcurrentHashMap<UUID, CompletableFuture<PlaybackSource>> pending = new ConcurrentHashMap<>();

    public PlaybackResolverService(PlaybackSourceStore store, CatalogTrackLookup catalogLookup,
                                    VideoSearchProvider videoSearchProvider, TrackMatcher matcher,
                                    MatchingProperties thresholds, ResolverProperties resolverProperties, Clock clock) {
        this.store = store;
        this.resolverProperties = resolverProperties;
        this.catalogLookup = catalogLookup;
        this.videoSearchProvider = videoSearchProvider;
        this.matcher = matcher;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * @throws TrackNotFoundException           the track id doesn't exist in catalog-svc
     * @throws NoConfidentPlaybackMatchException resolved (or previously resolved) to nothing playable —
     *                                           either no candidate cleared the medium threshold, or the
     *                                           best one is an unverified candidate awaiting manual review
     */
    public PlaybackSource resolve(UUID trackId) {
        // A verified match is final; a candidate / nothing-found outcome is retried once it's old
        // enough (new uploads appear, the matcher improves) — still never more than once per period.
        Optional<PlaybackSource> cached = store.findByTrackId(trackId, PlaybackProvider.YOUTUBE)
                .filter(s -> s.verified() || !isStale(s.updatedAt()));
        PlaybackSource source = cached.isPresent() ? cached.get() : coalescedResolve(trackId);
        if (!source.verified()) {
            throw new NoConfidentPlaybackMatchException(trackId);
        }
        return source;
    }

    private PlaybackSource coalescedResolve(UUID trackId) {
        CompletableFuture<PlaybackSource> future = pending.computeIfAbsent(trackId,
                id -> CompletableFuture.supplyAsync(() -> doResolve(id)));
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        } finally {
            pending.remove(trackId, future);
        }
    }

    private PlaybackSource doResolve(UUID trackId) {
        CanonicalTrack track = catalogLookup.findTrack(trackId).orElseThrow(() -> new TrackNotFoundException(trackId));
        List<VideoCandidate> candidates = videoSearchProvider.search(track);
        List<ScoredCandidate> ranked = matcher.rank(track, candidates);

        Instant now = clock.instant();
        UUID id = UUID.randomUUID();

        if (ranked.isEmpty()) {
            return store.upsert(nonePlaceholder(id, trackId, 0, now));
        }

        ScoredCandidate best = ranked.get(0);
        if (best.score() >= thresholds.highConfidenceThreshold()) {
            return store.upsert(fromCandidate(id, trackId, best, MatchMethod.AUTOMATIC, true, now));
        }
        if (best.score() >= thresholds.mediumConfidenceThreshold()) {
            // Stored, not thrown away — a future manual-verification flow can promote this later.
            return store.upsert(fromCandidate(id, trackId, best, MatchMethod.CANDIDATE, false, now));
        }
        return store.upsert(nonePlaceholder(id, trackId, best.score(), now));
    }

    private boolean isStale(Instant updatedAt) {
        return updatedAt == null || updatedAt.plus(resolverProperties.retryUnverifiedAfter()).isBefore(clock.instant());
    }

    private static PlaybackSource fromCandidate(UUID id, UUID trackId, ScoredCandidate scored, MatchMethod method,
                                                 boolean verified, Instant now) {
        VideoCandidate candidate = scored.candidate();
        return new PlaybackSource(id, trackId, PlaybackProvider.YOUTUBE, candidate.providerResourceId(),
                candidate.title(), candidate.channelId(), candidate.channelTitle(), candidate.durationMs(),
                scored.score(), method, verified, verified ? now : null, now, now);
    }

    private static PlaybackSource nonePlaceholder(UUID id, UUID trackId, int bestScoreSeen, Instant now) {
        return new PlaybackSource(id, trackId, PlaybackProvider.YOUTUBE, null, null, null, null, 0L,
                bestScoreSeen, MatchMethod.NONE, false, null, now, now);
    }
}
