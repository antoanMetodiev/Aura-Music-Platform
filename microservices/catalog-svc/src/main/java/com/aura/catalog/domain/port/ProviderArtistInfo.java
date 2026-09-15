package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ArtistAbout.SimilarArtist;

import java.util.List;

/**
 * One provider's view of an artist — every field optional, the service merges providers in order.
 * {@code links} are raw URLs; the service classifies them.
 */
public record ProviderArtistInfo(
        String biography,
        String biographyUrl,
        String biographyLanguage,
        Long listeners,
        Long playcount,
        List<String> tags,
        List<SimilarArtist> similar,
        List<String> links
) {
    public boolean hasBiography() {
        return biography != null && !biography.isBlank();
    }
}
