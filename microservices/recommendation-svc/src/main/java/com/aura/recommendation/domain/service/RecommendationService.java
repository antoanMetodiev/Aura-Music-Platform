package com.aura.recommendation.domain.service;

import com.aura.recommendation.config.AppConfig;
import com.aura.recommendation.config.RecommendationProperties;
import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.ArtistTag;
import com.aura.recommendation.domain.model.CandidateOrigin;
import com.aura.recommendation.domain.model.RecommendedTrack;
import com.aura.recommendation.domain.model.TrackRef;
import com.aura.recommendation.domain.port.CatalogLookup;
import com.aura.recommendation.domain.port.PlayabilityLookup;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import com.aura.recommendation.domain.source.ArtistCandidate;
import com.aura.recommendation.domain.source.ArtistCandidateSource;
import com.aura.recommendation.domain.source.CandidateRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Serving side: seed → candidate artists → their tracks → ranking → feed. No provider call ever
 * happens here; everything rests on the graph {@link ArtistGraphService} built in the background,
 * which is what lets a radio be a couple of queries and a fan-out of local catalog reads.
 *
 * <p>Today's seeds are artists, because that is all we can know about a listener: there is no auth,
 * no library and no listening history yet (Project-Info.md §24 assumes all three). When they arrive,
 * a taste profile is a longer, weighted seed list and a fourth {@link ArtistCandidateSource} — the
 * pipeline below, the ranking and the API stay exactly as they are.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    /** Artists used as seeds for the signed-out discovery feed. */
    private static final int COLD_START_SEEDS = 6;

    /** One home-page row. {@code key} is a stable identifier the frontend localizes; never a rendered title. */
    public record TrackSection(String key, List<RecommendedTrack> tracks) {
    }

    public record HomeFeed(List<TrackSection> sections, List<SimilarityGraphStore.TagCount> genres) {
    }

    private final CatalogLookup catalog;
    private final PlayabilityLookup playability;
    private final SimilarityGraphStore graph;
    private final List<ArtistCandidateSource> sources;
    private final TrackRanker ranker;
    private final RecommendationProperties properties;
    private final ExecutorService httpIo;
    private final Cache<String, List<RecommendedTrack>> feeds;
    private final Cache<String, List<ArtistRef>> artistLists;

    public RecommendationService(CatalogLookup catalog, PlayabilityLookup playability, SimilarityGraphStore graph,
                                 List<ArtistCandidateSource> sources, TrackRanker ranker,
                                 RecommendationProperties properties, @AppConfig.HttpIo ExecutorService httpIo) {
        this.catalog = catalog;
        this.playability = playability;
        this.graph = graph;
        this.sources = sources;
        this.ranker = ranker;
        this.properties = properties;
        this.httpIo = httpIo;
        this.feeds = Caffeine.newBuilder()
                .maximumSize(properties.feedMemoryEntries())
                .expireAfterWrite(properties.feedMemoryTtl())
                .build();
        this.artistLists = Caffeine.newBuilder()
                .maximumSize(properties.feedMemoryEntries())
                .expireAfterWrite(properties.feedMemoryTtl())
                .build();
    }

    // ── Artists ────────────────────────────────────────────────────────────────────────────

    /** "Fans also like": the artist's neighbours in the graph, hydrated from the catalog. */
    public List<ArtistRef> similarArtists(UUID artistId, int limit) {
        int effective = clamp(limit);
        return artistLists.get("similar:" + artistId + ":" + effective, key -> {
            ArtistRef seed = requireArtist(artistId);
            List<SimilarityGraphStore.Neighbour> neighbours =
                    graph.neighbours(seed.id(), effective, properties.reverseEdgeFactor());
            if (neighbours.isEmpty()) return List.of();
            return hydrate(neighbours.stream().map(SimilarityGraphStore.Neighbour::artistId).toList());
        });
    }

    // ── Feeds ──────────────────────────────────────────────────────────────────────────────

    /** An artist's radio: their own music plus what their listeners also like. */
    public List<RecommendedTrack> artistRadio(UUID artistId, int limit) {
        int effective = clamp(limit);
        return feeds.get("artist:" + artistId + ":" + effective, key -> {
            ArtistRef seed = requireArtist(artistId);
            return feedFrom(List.of(seed), tagNames(graph.tagsOf(seed.id())), true, Set.of(), effective);
        });
    }

    /**
     * A track's radio — the "recommended based on this track" of the track page. Seeded from the
     * track's artists rather than the track itself: resolving <em>track</em> names across providers is
     * far less reliable than artist names, so the graph is built at artist level and the track only
     * chooses the seed (and is itself excluded from the result).
     */
    public List<RecommendedTrack> trackRadio(UUID trackId, int limit) {
        int effective = clamp(limit);
        return feeds.get("track:" + trackId + ":" + effective, key -> {
            TrackRef track = catalog.findTrack(trackId).orElseThrow(() -> new SeedNotFoundException("Track", trackId));
            List<ArtistRef> seeds = track.artists() == null ? List.of() : track.artists().stream().limit(2).toList();
            if (seeds.isEmpty()) return trending(effective);
            List<String> tags = tagNames(graph.tagsOf(seeds.getFirst().id()));
            return feedFrom(seeds, tags, true, Set.of(track.id()), effective);
        });
    }

    /** A genre row: the artists carrying this tag, their best tracks. */
    public List<RecommendedTrack> tagTracks(String tag, int limit) {
        int effective = clamp(limit);
        String normalized = tag.trim().toLowerCase(java.util.Locale.ROOT);
        return feeds.get("tag:" + normalized + ":" + effective, key -> {
            List<ArtistCandidate> candidates = graph.artistsByTag(normalized, properties.maxSharedTagArtists()).stream()
                    .map(n -> new ArtistCandidate(n.artistId(), n.score(), CandidateOrigin.SHARED_TAG, null))
                    .toList();
            return rankTracksOf(candidates, Map.of(), normalized, Set.of(), effective);
        });
    }

    /** The genre grid: the tags the graph knows most artists under. */
    public List<SimilarityGraphStore.TagCount> topTags(int limit) {
        return graph.topTags(Math.min(Math.max(limit, 1), 100));
    }

    /**
     * Cold start, and the honest fallback everywhere else: the catalog's most popular tracks that can
     * actually be played. No taste involved — it is what we can say to someone we know nothing about.
     */
    public List<RecommendedTrack> trending(int limit) {
        int effective = clamp(limit);
        return feeds.get("trending:" + effective, key -> {
            // Over-fetch: popularity order is not playability order, and the unplayable ones are dropped.
            List<TrackRef> tracks = catalog.scanTracksByPopularity(2.0, new UUID(-1L, -1L), effective * 4);
            List<TrackRanker.Candidate> candidates = tracks.stream()
                    .filter(t -> t.primaryArtist() != null)
                    .map(t -> new TrackRanker.Candidate(t, 0, t.primaryArtist().id(), CandidateOrigin.POPULAR, null, null, null))
                    .toList();
            return rank(candidates, Set.of(), effective);
        });
    }

    /**
     * The signed-out home page: what is popular, plus a discovery row seeded from the catalog's
     * biggest artists — a stand-in for the taste profile that arrives with the library service.
     */
    public HomeFeed home(int perSection) {
        int effective = clamp(perSection);
        List<TrackSection> sections = new ArrayList<>();
        sections.add(new TrackSection("trending", trending(effective)));
        List<RecommendedTrack> discover = discover(effective);
        if (!discover.isEmpty()) sections.add(new TrackSection("discover", discover));
        return new HomeFeed(sections, topTags(12));
    }

    private List<RecommendedTrack> discover(int limit) {
        return feeds.get("discover:" + limit, key -> {
            List<ArtistRef> seeds = catalog.scanArtists(2.0, new UUID(-1L, -1L), COLD_START_SEEDS);
            if (seeds.isEmpty()) return List.of();
            List<String> tags = tagNames(graph.tagsOf(seeds.getFirst().id()));
            // includeSeeds = false: discovery means the neighbours, not the six artists everyone knows.
            return feedFrom(seeds, tags, false, Set.of(), limit);
        });
    }

    // ── Pipeline ───────────────────────────────────────────────────────────────────────────

    /** seed artists → candidate artists (every source) → their tracks → ranking. */
    private List<RecommendedTrack> feedFrom(List<ArtistRef> seeds, List<String> seedTags, boolean includeSeeds,
                                            Set<UUID> excludedTrackIds, int limit) {
        List<UUID> seedIds = seeds.stream().map(ArtistRef::id).toList();
        Map<UUID, String> seedNames = new LinkedHashMap<>();
        seeds.forEach(s -> seedNames.put(s.id(), s.name()));

        CandidateRequest request = new CandidateRequest(seedIds, seedTags, includeSeeds, limit);
        Map<UUID, ArtistCandidate> merged = new LinkedHashMap<>();
        for (ArtistCandidateSource source : sources) {
            for (ArtistCandidate candidate : source.candidates(request)) {
                merged.merge(candidate.artistId(), candidate, ArtistCandidate::merge);
            }
        }
        if (merged.isEmpty()) {
            log.debug("No candidates for seeds {} — the graph has nothing yet, falling back to trending", seedIds);
            return trending(limit);
        }

        // Enough artists to fill the feed even after the per-artist cap, and no more: each one costs a
        // catalog read.
        int artistsNeeded = Math.max(properties.maxSimilarArtists(),
                limit / Math.max(1, properties.maxTracksPerArtist()) + 2);
        List<ArtistCandidate> candidates = merged.values().stream()
                .sorted(Comparator.comparingDouble(ArtistCandidate::score).reversed())
                .limit(artistsNeeded)
                .toList();

        // The seed's heaviest tag labels whatever the tag source contributed: "because you like <seed>"
        // does not apply to an artist that arrived through the genre, and a reason with nothing in it
        // is worse than a slightly coarse one.
        String tagLabel = seedTags.isEmpty() ? null : seedTags.getFirst();
        List<RecommendedTrack> ranked = rankTracksOf(candidates, seedNames, tagLabel, excludedTrackIds, limit);
        return ranked.isEmpty() ? trending(limit) : ranked;
    }

    /** Fetches each candidate artist's tracks (in parallel — they are independent local reads) and ranks them. */
    private List<RecommendedTrack> rankTracksOf(List<ArtistCandidate> candidates, Map<UUID, String> seedNames,
                                                String tag, Set<UUID> excludedTrackIds, int limit) {
        if (candidates.isEmpty()) return List.of();
        int perArtist = properties.tracksPerCandidateArtist();

        List<CompletableFuture<List<TrackRanker.Candidate>>> futures = candidates.stream()
                .map(candidate -> CompletableFuture.supplyAsync(() -> catalog.topTracks(candidate.artistId(), perArtist)
                                .stream()
                                .map(track -> new TrackRanker.Candidate(track, candidate.score(), candidate.artistId(),
                                        candidate.origin(), candidate.seedArtistId(),
                                        seedName(seedNames, candidate.seedArtistId()), tag))
                                .toList(),
                        httpIo))
                .toList();

        List<TrackRanker.Candidate> trackCandidates = new ArrayList<>();
        for (CompletableFuture<List<TrackRanker.Candidate>> future : futures) {
            try {
                trackCandidates.addAll(future.join());
            } catch (java.util.concurrent.CompletionException e) {
                // One artist's tracks failing must not lose the other nineteen.
                log.warn("Could not read a candidate artist's tracks: {}", e.getMessage());
            }
        }
        return rank(trackCandidates, excludedTrackIds, limit);
    }

    private List<RecommendedTrack> rank(List<TrackRanker.Candidate> candidates, Set<UUID> excludedTrackIds, int limit) {
        if (candidates.isEmpty()) return List.of();
        List<UUID> trackIds = candidates.stream().map(c -> c.track().id()).distinct().toList();

        Set<UUID> playable;
        boolean playabilityKnown;
        try {
            playable = playability.playableAmong(trackIds);
            playabilityKnown = true;
        } catch (PlaybackUnavailableException e) {
            // Serve an optimistic feed rather than none (Project-Info.md §48).
            log.warn("playback-svc unavailable, serving {} candidates unfiltered", trackIds.size());
            playable = Set.of();
            playabilityKnown = false;
        }
        return ranker.rank(candidates, playable, playabilityKnown, excludedTrackIds, limit);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private ArtistRef requireArtist(UUID artistId) {
        return catalog.findArtist(artistId).orElseThrow(() -> new SeedNotFoundException("Artist", artistId));
    }

    /** Hydrates artist ids in parallel, dropping any the catalog no longer has, order preserved. */
    private List<ArtistRef> hydrate(List<UUID> artistIds) {
        List<CompletableFuture<java.util.Optional<ArtistRef>>> futures = artistIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> catalog.findArtist(id), httpIo))
                .toList();
        List<ArtistRef> out = new ArrayList<>();
        for (CompletableFuture<java.util.Optional<ArtistRef>> future : futures) {
            try {
                future.join().ifPresent(out::add);
            } catch (java.util.concurrent.CompletionException e) {
                log.warn("Could not hydrate a neighbour artist: {}", e.getMessage());
            }
        }
        return out;
    }

    /**
     * A tag-seeded feed has no seed artist, and {@code Map.of()} throws on a null key rather than
     * answering "no such entry" — which is exactly the lookup a tag feed makes for every candidate.
     */
    private static String seedName(Map<UUID, String> seedNames, UUID seedArtistId) {
        return seedArtistId == null ? null : seedNames.get(seedArtistId);
    }

    private static List<String> tagNames(List<ArtistTag> tags) {
        return tags.stream().map(ArtistTag::tag).toList();
    }

    private int clamp(int limit) {
        if (limit <= 0) return properties.defaultLimit();
        return Math.min(limit, properties.maxLimit());
    }
}
