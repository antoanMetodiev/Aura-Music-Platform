package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Lyrics;

import java.util.List;

/** Provider-agnostic lyrics as returned by a {@link LyricsProvider}, before we attach them to a track. */
public record ProviderLyrics(boolean instrumental, List<Lyrics.SyncedLine> synced, String plain) {
}
