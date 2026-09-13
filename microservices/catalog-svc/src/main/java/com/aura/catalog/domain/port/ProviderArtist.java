package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.ProviderReference;

/** Artist as normalized by a provider adapter — no canonical id yet; the catalog assigns one on persist. */
public record ProviderArtist(ProviderReference ref, String name, Artwork artwork, double popularity) {
}
