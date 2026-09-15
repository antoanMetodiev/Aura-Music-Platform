package com.aura.catalog.adapter.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /tracks/{id}/lyrics}. {@code synced} and {@code plain} are each {@code null} when the
 * provider only has the other; an {@code instrumental} track has neither.
 */
public record LyricsResponse(
        UUID trackId,
        String provider,
        boolean instrumental,
        List<SyncedLineResponse> synced,
        String plain
) {
    public record SyncedLineResponse(long timeMs, String text) {
    }
}
