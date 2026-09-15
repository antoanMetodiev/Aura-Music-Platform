package com.aura.catalog.domain.model;

/**
 * External music metadata providers we can reference. Never leaks into API responses as a primary key.
 * {@code LRCLIB} supplies lyrics only (see {@link Lyrics}) — it never appears in a {@link ProviderReference}.
 */
public enum Provider {
    TIDAL,
    LRCLIB,
    /** Artist biography, tags, similar artists, listener stats. */
    LASTFM,
    /** Artist profile text and outside links. */
    DISCOGS
}
