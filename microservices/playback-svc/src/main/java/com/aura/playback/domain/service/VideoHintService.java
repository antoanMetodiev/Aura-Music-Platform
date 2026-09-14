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
import com.aura.playback.domain.port.VideoSearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pre-resolves playback sources without spending YouTube searches: walks the catalog, asks
 * {@link KnownVideoLookup} (MusicBrainz, by ISRC) whether a YouTube video is already known for the
 * recording, validates that one video through the same {@link TrackMatcher} scoring a search result
 * would face, and stores it as a verified source when it clears the high-confidence threshold.
 *
 * <p>Every track visited gets a {@link Hint} row whatever happened — "no link", "not in MusicBrainz",
 * "no ISRC", "link rejected" — and is never asked about again (§20: the answer won't change, so the
 * provider call would be wasted). A validated link costs one {@code videos.list} unit instead of the
 * hundred a search costs.
 */
@Service
public class VideoHintService {

    private static final Logger log = LoggerFactory.getLogger(VideoHintService.class);

    public record PageOutcome(int scanned, int checked, int matched, boolean endOfCatalog, Cursor cursor) {
    }

    private final VideoHintStore hints;
    private final PlaybackSourceStore sources;
    private final CatalogTrackLookup catalog;
    private final KnownVideoLookup knownVideos;
    private final VideoSearchProvider videos;
    private final TrackMatcher matcher;
    private final MatchingProperties thresholds;
    private final Clock clock;
    private final AtomicReference<String> lastMatched = new AtomicReference<>();

    public VideoHintService(VideoHintStore hints, PlaybackSourceStore sources, CatalogTrackLookup catalog,
                            KnownVideoLookup knownVideos, VideoSearchProvider videos, TrackMatcher matcher,
                            MatchingProperties thresholds, Clock clock) {
        this.hints = hints;
        this.sources = sources;
        this.catalog = catalog;
        this.knownVideos = knownVideos;
        this.videos = videos;
        this.matcher = matcher;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    /**
     * Processes the next page of the catalog after the persisted cursor. Reaching the end wraps the
     * cursor back to the start (tracks created since the walk began are picked up on the next pass;
     * everything already checked is skipped cheaply).
     *
     * @throws PlaybackProviderUnavailableException / CatalogServiceUnavailableException when an
     *         upstream is down — nothing is recorded for the tracks of this page, the caller backs off.
     */
    public PageOutcome processNextPage(int pageSize) {
        Cursor cursor = hints.cursor().orElseGet(() -> Cursor.start(0));
        List<ScannedTrack> page = catalog.scan(cursor.createdAfter(), cursor.afterId(), pageSize);
        if (page.isEmpty()) {
            Cursor restart = Cursor.start(cursor.passes() + 1);
            hints.saveCursor(restart);
            return new PageOutcome(0, 0, 0, true, restart);
        }

        int checked = 0;
        int matched = 0;
        for (ScannedTrack scanned : page) {
            CanonicalTrack track = scanned.track();
            if (hints.isChecked(track.id())) continue;
            if (sources.findByTrackId(track.id(), PlaybackProvider.YOUTUBE).map(PlaybackSource::verified).orElse(false)) {
                // Already resolved by a real play — nothing to gain.
                hints.save(new Hint(track.id(), track.isrc(), HintOutcome.MATCHED, null, null, clock.instant()));
                continue;
            }
            checked++;
            if (check(track)) matched++;
        }

        ScannedTrack last = page.getLast();
        Cursor next = new Cursor(last.createdAt(), last.track().id(), cursor.passes());
        hints.saveCursor(next);
        return new PageOutcome(page.size(), checked, matched, false, next);
    }

    /** One track: MusicBrainz → (optional) YouTube videos.list → matcher. Returns true when a verified source was stored. */
    private boolean check(CanonicalTrack track) {
        if (track.isrc() == null || track.isrc().isBlank()) {
            hints.save(new Hint(track.id(), null, HintOutcome.NO_ISRC, null, null, clock.instant()));
            return false;
        }
        KnownVideoLookup.Result result = knownVideos.lookupByIsrc(track.isrc());
        switch (result.outcome()) {
            case NOT_FOUND -> {
                hints.save(new Hint(track.id(), track.isrc(), HintOutcome.NOT_FOUND, null, null, clock.instant()));
                return false;
            }
            case NO_LINK -> {
                hints.save(new Hint(track.id(), track.isrc(), HintOutcome.NO_LINK, null, null, clock.instant()));
                return false;
            }
            case LINK_FOUND -> {
                String videoId = result.youtubeVideoId().orElseThrow();
                Optional<VideoCandidate> candidate = videos.findById(videoId);
                List<ScoredCandidate> ranked = candidate.map(c -> matcher.rank(track, List.of(c))).orElse(List.of());
                if (ranked.isEmpty() || ranked.getFirst().score() < thresholds.highConfidenceThreshold()) {
                    int score = ranked.isEmpty() ? 0 : ranked.getFirst().score();
                    hints.save(new Hint(track.id(), track.isrc(), HintOutcome.REJECTED, videoId, score, clock.instant()));
                    log.info("Video hint rejected for '{}' ({}): YouTube {} scored {}", track.title(), track.primaryArtist(), videoId, score);
                    return false;
                }
                ScoredCandidate best = ranked.getFirst();
                Instant now = clock.instant();
                VideoCandidate c = best.candidate();
                sources.upsert(new PlaybackSource(UUID.randomUUID(), track.id(), PlaybackProvider.YOUTUBE,
                        c.providerResourceId(), c.title(), c.channelId(), c.channelTitle(), c.durationMs(),
                        best.score(), MatchMethod.AUTOMATIC, true, now, now, now));
                hints.save(new Hint(track.id(), track.isrc(), HintOutcome.MATCHED, videoId, best.score(), now));
                lastMatched.set(track.primaryArtist() + " - " + track.title() + " -> " + videoId + " (" + best.score() + ")");
                log.info("Video hint matched: '{}' ({}) -> YouTube {} [{}] score {}", track.title(), track.primaryArtist(), videoId, c.channelTitle(), best.score());
                return true;
            }
        }
        return false;
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
