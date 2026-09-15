package com.aura.catalog.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything we show about an artist beyond their music: biography, community tags, artists their
 * listeners also like, listener stats and outside links. Any part may be missing; {@code isEmpty()}
 * is the recorded "nothing found anywhere" case.
 */
public record ArtistAbout(
        UUID artistId,
        Biography biography,
        Long listeners,
        Long playcount,
        List<String> tags,
        List<SimilarArtist> similar,
        List<ExternalLink> links,
        Instant fetchedAt
) {
    /** Full text plus who wrote it — Last.fm's is CC BY-SA and must be credited with a link. */
    public record Biography(String text, Provider source, String url, String language) {
    }

    /** As the provider names it; {@code artist} is set when we have that artist in our own catalog. */
    public record SimilarArtist(String name, String url, Artist artist) {
    }

    /** {@code type} is a normalized host label (instagram, youtube, website, ...) the UI maps to an icon. */
    public record ExternalLink(String type, String url) {
    }

    public boolean isEmpty() {
        return biography == null && listeners == null && playcount == null
                && tags.isEmpty() && similar.isEmpty() && links.isEmpty();
    }
}
