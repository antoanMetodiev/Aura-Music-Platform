package com.aura.catalog.adapter.provider.lrclib.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One LRCLIB record — {@code GET /api/get} returns exactly this, {@code GET /api/search} an array of them. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LrclibLyricsResponse(
        long id,
        String trackName,
        String artistName,
        String albumName,
        /** Seconds, may carry a fraction. */
        Double duration,
        boolean instrumental,
        String plainLyrics,
        String syncedLyrics
) {
    public boolean hasAnyText() {
        return instrumental
                || (syncedLyrics != null && !syncedLyrics.isBlank())
                || (plainLyrics != null && !plainLyrics.isBlank());
    }
}
