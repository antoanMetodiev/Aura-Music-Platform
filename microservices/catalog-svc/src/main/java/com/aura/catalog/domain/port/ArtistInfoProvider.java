package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Provider;

import java.util.Optional;

/**
 * Outbound port for artist "About" data. Implementations are consulted in {@code @Order}: Last.fm
 * (biography, tags, similar, stats) then Discogs (links, profile as a biography fallback). Empty =
 * the provider doesn't know the artist; an outage surfaces as
 * {@link com.aura.catalog.domain.service.ProviderUnavailableException}.
 */
public interface ArtistInfoProvider {

    Provider provider();

    /** @param language ISO 639-1 code the caller would like the biography in; providers fall back to English. */
    Optional<ProviderArtistInfo> find(Artist artist, String language);
}
