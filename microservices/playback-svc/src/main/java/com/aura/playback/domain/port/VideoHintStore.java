package com.aura.playback.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence for the video-hint walk: what we've checked, and where the catalog scan is up to. */
public interface VideoHintStore {

    enum HintOutcome { MATCHED, REJECTED, NO_LINK, NOT_FOUND, NO_ISRC }

    /** A source that contributed to a hint. Declaration order is the chain order and the order they're stored in. */
    enum HintSource {
        /** MusicBrainz was asked for this track's ISRC. */
        MUSICBRAINZ,
        /** Discogs was asked by artist + title (its release pages carry the official videos). */
        DISCOGS,
        /** Part of the answer was inherited from another track with the same ISRC instead of asked again. */
        SIBLING
    }

    /**
     * One row per track. {@code sources} lists every source that has been asked (or inherited) so far;
     * a non-matched hint missing a source that could still be asked is incomplete and gets revisited.
     */
    record Hint(UUID trackId, String isrc, Set<HintSource> sources, HintOutcome outcome, String youtubeId,
                Integer matchScore, Instant checkedAt) {
        public Hint {
            sources = sources.isEmpty() ? EnumSet.noneOf(HintSource.class) : EnumSet.copyOf(sources);
        }

        /** Sources whose answer this hint actually carries — i.e. the ones a sibling may inherit. */
        public Set<HintSource> askedSources() {
            EnumSet<HintSource> asked = EnumSet.copyOf(sources);
            asked.remove(HintSource.SIBLING);
            return asked;
        }
    }

    /**
     * Position in the most-popular-first walk: the next page is strictly after
     * {@code (popularityBelow, afterId)} in {@code (popularity DESC, id DESC)} order.
     */
    record Cursor(double popularityBelow, UUID afterId, int passes) {
        /** Popularity is 0..1, so 2 with the max UUID is "before the very first row". */
        public static Cursor start(int passes) { return new Cursor(2.0, new UUID(-1L, -1L), passes); }
    }

    record Stats(long checked, long matched, long rejected, long noLink, long notFound, long noIsrc,
                 long fromSiblings, long matchedByMusicBrainz, long matchedByDiscogs) {
    }

    /** Existing hints for these tracks — one query for the whole page. */
    Map<UUID, Hint> findByTrackIds(Collection<UUID> trackIds);

    /**
     * The most useful hint recorded for <em>another</em> track with this ISRC: a {@code MATCHED} one if
     * any, else {@code REJECTED} (its link can be re-validated), else the miss that asked the most sources.
     */
    Optional<Hint> findSibling(String isrc, UUID excludingTrackId);

    /** Insert or replace the track's row. */
    void save(Hint hint);

    Optional<Cursor> cursor();

    void saveCursor(Cursor cursor);

    Stats stats();

    List<Hint> recent(int limit);
}
