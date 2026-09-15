package com.aura.catalog.domain.port;

/**
 * What a lyrics provider gets to match on. Built by the service from our canonical {@code Track};
 * {@code albumTitle} and {@code isrc} may be {@code null}.
 */
public record LyricsQuery(String artistName, String title, String albumTitle, long durationMs, String isrc) {
}
