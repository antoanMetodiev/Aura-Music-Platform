package com.aura.playback.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for the video-hint walk: what we've checked, and where the catalog scan is up to. */
public interface VideoHintStore {

    enum HintOutcome { MATCHED, REJECTED, NO_LINK, NOT_FOUND, NO_ISRC }

    record Hint(UUID trackId, String isrc, HintOutcome outcome, String youtubeId, Integer matchScore, Instant checkedAt) {
    }

    record Cursor(Instant createdAfter, UUID afterId, int passes) {
        public static Cursor start(int passes) { return new Cursor(Instant.EPOCH, new UUID(0L, 0L), passes); }
    }

    record Stats(long checked, long matched, long rejected, long noLink, long notFound, long noIsrc) {
    }

    boolean isChecked(UUID trackId);

    void save(Hint hint);

    Optional<Cursor> cursor();

    void saveCursor(Cursor cursor);

    Stats stats();

    List<Hint> recent(int limit);
}
