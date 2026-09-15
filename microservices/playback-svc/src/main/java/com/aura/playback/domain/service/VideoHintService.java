package com.aura.playback.domain.service;

import com.aura.playback.config.MatchingProperties;
import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.MatchMethod;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.PlaybackSource;
import com.aura.playback.domain.model.ScoredCandidate;
import com.aura.playback.domain.model.VideoCandidate;
import com.aura.playback.domain.port.CatalogTrackLookup;
import com.aura.playback.domain.port.CatalogTrackLookup.ScannedTrack;
import com.aura.playback.domain.port.KnownVideoLookup;
import com.aura.playback.domain.port.PlaybackSourceStore;
import com.aura.playback.domain.port.VideoHintStore;
import com.aura.playback.domain.port.VideoHintStore.Cursor;
import com.aura.playback.domain.port.VideoHintStore.Hint;
import com.aura.playback.domain.port.VideoHintStore.HintOutcome;
import com.aura.playback.domain.port.VideoHintStore.HintSource;
import com.aura.playback.domain.port.VideoSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pre-resolves playback sources without spending YouTube searches: walks the catalog most-popular-first
 * and asks a chain of {@link KnownVideoLookup}s (MusicBrainz by ISRC, then Discogs by name) whether a
 * YouTube video is already known for the recording, validates the link through the same
 * {@link TrackMatcher} scoring a search result would face, and stores it as a verified source when it
 * clears the high-confidence threshold. A validated link costs one {@code videos.list} unit instead of
 * the hundred a search costs.
 *
 * <p>Every track has at most one {@link Hint} row, updated in place. It lists every source asked so far;
 * a source is asked exactly once per track (§20: its answer won't change). A row that isn't matched and
 * still lacks a source that could be asked — every MusicBrainz miss recorded before Discogs existed —
 * is incomplete: the walk revisits it and asks only the missing source.
 *
 * <p>Half the catalog shares an ISRC with another track (re-releases, compilations — the same recording).
 * A sibling's verified source is copied outright, and a sibling's misses are inherited per source, so
 * each provider is asked about a recording once, not once per edition.
 */
@Service
public class VideoHintService {

    private static final Logger log = LoggerFactory.getLogger(VideoHintService.class);

    /** How many of one provider's pre-ranked links may each cost a YouTube unit before giving up on that provider. */
    private static final int MAX_VALIDATIONS_PER_PROVIDER = 2;

    /**
     * @param scanned  tracks in the page
     * @param checked  tracks that cost at least one provider call
     * @param reused   tracks answered entirely from a sibling (no provider call)
     * @param matched  verified sources stored (by either route)
     */
    public record PageOutcome(int scanned, int checked, int reused, int matched, boolean endOfCatalog, Cursor cursor) {
        /** Nothing left the process — the page was pure database work. */
        public boolean quiet() {
            return checked == 0;
        }
    }

    private final VideoHintStore hints;
    private final PlaybackSourceStore sources;
    private final CatalogTrackLookup catalog;
    /** In chain order (@Order on the adapters): MusicBrainz by ISRC first, Discogs by name second. */
    private final List<KnownVideoLookup> lookups;
    private final VideoSearchProvider videos;
    private final TrackMatcher matcher;
    private final MatchingProperties thresholds;
    private final Clock clock;
    private final AtomicReference<String> lastMatched = new AtomicReference<>();

    public VideoHintService(VideoHintStore hints, PlaybackSourceStore sources, CatalogTrackLookup catalog,
                            List<KnownVideoLookup> lookups, VideoSearchProvider videos, TrackMatcher matcher,
                            MatchingProperties thresholds, Clock clock) {
        this.hints = hints;
        this.sources = sources;
        this.catalog = catalog;
        this.lookups = lookups;
        this.videos = videos;
        this.matcher = matcher;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Processes the next page of the catalog after the persisted cursor. Reaching the end wraps the
     * cursor back to the top (tracks added since the walk began are picked up on the next pass;
     * complete hints are skipped from one query per page).
     *
     * @throws PlaybackProviderUnavailableException / CatalogServiceUnavailableException when an
     *         upstream is down — nothing is recorded for the tracks of this page, the caller backs off.
     */
    public synchronized PageOutcome processNextPage(int pageSize) {
        // One page at a time, process-wide: the worker thread and the /process-next debug endpoint share
        // the cursor, and interleaving them made both handle (and pay for) the same tracks.
        Cursor cursor = hints.cursor().orElseGet(() -> Cursor.start(0));
        List<ScannedTrack> page = catalog.scanByPopularity(cursor.popularityBelow(), cursor.afterId(), pageSize);
        if (page.isEmpty()) {
            Cursor restart = Cursor.start(cursor.passes() + 1);
            hints.saveCursor(restart);
            return new PageOutcome(0, 0, 0, 0, true, restart);
        }

        Map<UUID, Hint> existing = hints.findByTrackIds(page.stream().map(s -> s.track().id()).toList());
        int checked = 0;
        int reused = 0;
        int matched = 0;
        for (ScannedTrack scanned : page) {
            CanonicalTrack track = scanned.track();
            Hint current = existing.get(track.id());
            if (current != null && current.outcome() == HintOutcome.MATCHED) continue;

            if (current == null && sources.findByTrackId(track.id(), PlaybackProvider.YOUTUBE).map(PlaybackSource::verified).orElse(false)) {
                // Already resolved by a real play — nothing to gain.
                hints.save(new Hint(track.id(), track.isrc(), EnumSet.noneOf(HintSource.class), HintOutcome.MATCHED, null, null, clock.instant()));
                continue;
            }

            EnumSet<HintSource> missing = missingSources(track, current);
            if (missing.isEmpty()) continue; // a complete miss: every source that can be asked has been

            Step step = resolve(track, current, missing);
            if (step.calledProvider()) checked++; else reused++;
            if (step.matched()) matched++;
        }

        ScannedTrack last = page.getLast();
        Cursor next = new Cursor(last.popularity(), last.track().id(), cursor.passes());
        hints.saveCursor(next);
        return new PageOutcome(page.size(), checked, reused, matched, false, next);
    }

    /** Sources that can be asked about this track and haven't been (asked or inherited) yet. */
    private EnumSet<HintSource> missingSources(CanonicalTrack track, Hint current) {
        EnumSet<HintSource> missing = EnumSet.noneOf(HintSource.class);
        for (KnownVideoLookup lookup : lookups) {
            if (lookup.supports(track)) missing.add(lookup.source());
        }
        if (current != null) missing.removeAll(current.askedSources());
        return missing;
    }

    private record Step(boolean calledProvider, boolean matched) {
    }

    /**
     * Fills in the missing sources for one track: first from a sibling with the same ISRC (a verified
     * source is copied; misses are inherited per source), then by asking whatever is still missing, in
     * chain order, until a link validates. The hint row is rewritten with everything known by the end.
     */
    private Step resolve(CanonicalTrack track, Hint current, EnumSet<HintSource> missing) {
        EnumSet<HintSource> known = current == null ? EnumSet.noneOf(HintSource.class) : EnumSet.copyOf(current.sources());
        HintOutcome outcome = current == null ? null : current.outcome();
        String rejectedId = current != null && current.outcome() == HintOutcome.REJECTED ? current.youtubeId() : null;
        Integer rejectedScore = current != null && current.outcome() == HintOutcome.REJECTED ? current.matchScore() : null;
        boolean hasIsrc = track.isrc() != null && !track.isrc().isBlank();

        // ── Sibling first: free ────────────────────────────────────────────────────────────────
        if (hasIsrc) {
            Optional<Hint> sibling = hints.findSibling(track.isrc(), track.id());
            if (sibling.isPresent()) {
                Hint s = sibling.get();
                if (s.outcome() == HintOutcome.MATCHED) {
                    Optional<PlaybackSource> source = sources.findVerifiedByIsrc(track.isrc(), PlaybackProvider.YOUTUBE);
                    if (source.isPresent()) {
                        Instant now = clock.instant();
                        PlaybackSource copy = copyForTrack(source.get(), track, now);
                        sources.upsert(copy);
                        EnumSet<HintSource> via = EnumSet.copyOf(s.askedSources());
                        via.add(HintSource.SIBLING);
                        hints.save(new Hint(track.id(), track.isrc(), via, HintOutcome.MATCHED, copy.providerResourceId(), copy.matchScore(), now));
                        log.debug("Video hint copied from ISRC sibling: '{}' ({}) -> YouTube {}", track.title(), track.primaryArtist(), copy.providerResourceId());
                        return new Step(false, true);
                    }
                    // Hint says matched but the source is gone — treat its link as one more to validate.
                    if (s.youtubeId() != null) {
                        known = withSibling(known, s.askedSources());
                        missing.removeAll(s.askedSources());
                        int score = validate(track, s.youtubeId(), known);
                        if (score >= thresholds.highConfidenceThreshold()) return new Step(true, true);
                        rejectedId = s.youtubeId();
                        rejectedScore = score;
                        outcome = HintOutcome.REJECTED;
                    }
                } else {
                    // Inherit the sibling's misses for the sources we still lack — each provider is asked
                    // about a recording once, not once per edition.
                    EnumSet<HintSource> inherited = EnumSet.copyOf(s.askedSources());
                    inherited.retainAll(missing);
                    if (!inherited.isEmpty()) {
                        missing.removeAll(inherited);
                        known.addAll(inherited);
                        known.add(HintSource.SIBLING);
                        outcome = worse(outcome, s.outcome());
                        if (s.outcome() == HintOutcome.REJECTED && s.youtubeId() != null && rejectedId == null) {
                            // The sibling's link failed for the sibling; ours may differ in title — worth one unit.
                            int score = validate(track, s.youtubeId(), known);
                            if (score >= thresholds.highConfidenceThreshold()) return new Step(true, true);
                            rejectedId = s.youtubeId();
                            rejectedScore = score;
                            outcome = HintOutcome.REJECTED;
                        }
                    }
                }
            }
        }

        // ── Then the providers still missing, in chain order ───────────────────────────────────
        boolean called = false;
        for (KnownVideoLookup lookup : lookups) {
            if (!missing.contains(lookup.source())) continue;
            called = true;
            KnownVideoLookup.Result result = lookup.lookup(track);
            known.add(lookup.source());
            switch (result.outcome()) {
                case NOT_FOUND -> outcome = worse(outcome, HintOutcome.NOT_FOUND);
                case NO_LINK -> outcome = worse(outcome, HintOutcome.NO_LINK);
                case LINK_FOUND -> {
                    for (String videoId : rankKnownVideos(track, result.videos())) {
                        int score = validate(track, videoId, known);
                        if (score >= thresholds.highConfidenceThreshold()) return new Step(true, true);
                        if (rejectedScore == null || score > rejectedScore) {
                            rejectedId = videoId;
                            rejectedScore = score;
                        }
                        outcome = HintOutcome.REJECTED;
                    }
                }
            }
        }

        if (outcome == null) outcome = HintOutcome.NO_ISRC;
        hints.save(new Hint(track.id(), track.isrc(), known, outcome, rejectedId, rejectedScore, clock.instant()));
        return new Step(called, false);
    }

    /** Miss outcomes ranked by how much they tell us: a rejected link > "exists, no link" > "unknown". */
    private static HintOutcome worse(HintOutcome a, HintOutcome b) {
        if (a == null) return b;
        if (b == null) return a;
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(HintOutcome o) {
        return switch (o) {
            case MATCHED -> 4;
            case REJECTED -> 3;
            case NO_LINK -> 2;
            case NOT_FOUND -> 1;
            case NO_ISRC -> 0;
        };
    }

    private static EnumSet<HintSource> withSibling(Set<HintSource> known, Set<HintSource> inherited) {
        EnumSet<HintSource> all = EnumSet.copyOf(known);
        all.addAll(inherited);
        all.add(HintSource.SIBLING);
        return all;
    }

    /**
     * Orders a provider's links by how well what it says about them fits the track, using the same
     * matcher a real search result faces (no channel is known, so only title/duration/negative
     * signals count) — and drops anything that would not even be a candidate. Links the provider knows
     * nothing about (MusicBrainz gives an id only) can't be pre-ranked and go through as they are.
     */
    private List<String> rankKnownVideos(CanonicalTrack track, List<KnownVideoLookup.KnownVideo> known) {
        List<VideoCandidate> describable = known.stream()
                .filter(v -> v.title() != null && !v.title().isBlank())
                .map(v -> new VideoCandidate(v.youtubeVideoId(), v.title(), null, null, null, v.durationMs(), true))
                .toList();
        List<String> ranked = new ArrayList<>(known.stream()
                .filter(v -> v.title() == null || v.title().isBlank())
                .map(KnownVideoLookup.KnownVideo::youtubeVideoId)
                .toList());
        matcher.rank(track, describable).stream()
                .filter(s -> s.score() >= thresholds.mediumConfidenceThreshold())
                .limit(MAX_VALIDATIONS_PER_PROVIDER)
                .forEach(s -> ranked.add(s.candidate().providerResourceId()));
        return ranked;
    }

    /**
     * One {@code videos.list} unit: fetch the video, score it exactly as a search result would be scored,
     * store a verified source when it clears the high-confidence threshold (and record the matched hint,
     * with {@code sourcesSoFar} as the sources known by now). Returns the score; the caller records a
     * rejection itself once it has tried everything it wants to.
     */
    private int validate(CanonicalTrack track, String videoId, Set<HintSource> sourcesSoFar) {
        Optional<VideoCandidate> candidate = videos.findById(videoId);
        List<ScoredCandidate> ranked = candidate.map(c -> matcher.rank(track, List.of(c))).orElse(List.of());
        if (ranked.isEmpty() || ranked.getFirst().score() < thresholds.highConfidenceThreshold()) {
            int score = ranked.isEmpty() ? 0 : ranked.getFirst().score();
            log.info("Video hint rejected for '{}' ({}): YouTube {} scored {}", track.title(), track.primaryArtist(), videoId, score);
            return score;
        }
        ScoredCandidate best = ranked.getFirst();
        Instant now = clock.instant();
        VideoCandidate c = best.candidate();
        sources.upsert(new PlaybackSource(UUID.randomUUID(), track.id(), PlaybackProvider.YOUTUBE, track.isrc(),
                c.providerResourceId(), c.title(), c.channelId(), c.channelTitle(), c.durationMs(),
                best.score(), MatchMethod.AUTOMATIC, true, now, now, now));
        hints.save(new Hint(track.id(), track.isrc(), sourcesSoFar, HintOutcome.MATCHED, videoId, best.score(), now));
        lastMatched.set(track.primaryArtist() + " - " + track.title() + " -> " + videoId + " (" + best.score() + ")");
        log.info("Video hint matched: '{}' ({}) -> YouTube {} [{}] score {} via {}", track.title(), track.primaryArtist(), videoId, c.channelTitle(), best.score(), sourcesSoFar);
        return best.score();
    }

    /** The sibling's verified video, re-keyed to this track: same recording, same video, same confidence. */
    static PlaybackSource copyForTrack(PlaybackSource sibling, CanonicalTrack track, Instant now) {
        return new PlaybackSource(UUID.randomUUID(), track.id(), sibling.provider(), track.isrc(),
                sibling.providerResourceId(), sibling.title(), sibling.channelId(), sibling.channelTitle(),
                sibling.durationMs(), sibling.matchScore(), MatchMethod.ISRC_SIBLING, true, now, now, now);
    }

    public VideoHintStore.Stats stats() {
        return hints.stats();
    }

    public Optional<Cursor> cursor() {
        return hints.cursor();
    }

    public List<Hint> recent(int limit) {
        return hints.recent(limit);
    }

    public Optional<String> lastMatched() {
        return Optional.ofNullable(lastMatched.get());
    }
}
