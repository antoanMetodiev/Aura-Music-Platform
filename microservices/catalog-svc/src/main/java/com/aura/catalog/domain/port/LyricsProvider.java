package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Provider;

import java.util.Optional;

/**
 * Outbound port for lyrics (todo.md §2.1). LRCLIB for dev/MVP, a licensed provider (Musixmatch) for
 * production — same port, swapped by config, so nothing above the adapter knows which one answers.
 *
 * <p>Empty = the provider has no text for this recording (a normal outcome, cached as a miss).
 * A provider outage must surface as {@link com.aura.catalog.domain.service.ProviderUnavailableException}
 * instead, so the miss is <em>not</em> recorded.
 */
public interface LyricsProvider {

    Provider provider();

    Optional<ProviderLyrics> find(LyricsQuery query);
}
