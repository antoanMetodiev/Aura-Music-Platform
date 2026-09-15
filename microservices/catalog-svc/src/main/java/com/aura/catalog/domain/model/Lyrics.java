package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lyrics for one canonical track (todo.md §2.1). {@code synced} is the time-coded text (LRC-style, one
 * entry per line, ordered by time) and {@code plain} the same text without timing; either may be
 * {@code null} when the provider only has the other. An {@code instrumental} track has neither.
 */
public record Lyrics(
        UUID trackId,
        Provider provider,
        boolean instrumental,
        List<SyncedLine> synced,
        String plain,
        Instant fetchedAt
) {
    /** One line of synced lyrics; {@code text} may be empty for an instrumental pause. */
    public record SyncedLine(long timeMs, String text) {
    }

    public boolean hasSynced() {
        return synced != null && !synced.isEmpty();
    }

    public boolean hasPlain() {
        return plain != null && !plain.isBlank();
    }
}
