package com.aura.recommendation.domain.model;

import java.util.UUID;

/**
 * Why a track is being recommended, as structured data rather than a sentence — the frontend
 * localizes it ("Because you like X" / "Защото слушаш X"). Never a rendered string from the backend.
 */
public record Reason(Kind kind, UUID artistId, String artistName, String tag) {

    public enum Kind {
        /** The seed artist's own music. */
        BY_SEED_ARTIST,
        /** Listeners of the seed artist also like this one. */
        SIMILAR_TO_ARTIST,
        /** Shares a genre/tag with the seed. */
        SHARED_TAG,
        /** Popular in our catalog — cold start, no graph evidence. */
        POPULAR
    }

    public static Reason bySeedArtist(ArtistRef artist) {
        return new Reason(Kind.BY_SEED_ARTIST, artist.id(), artist.name(), null);
    }

    public static Reason similarTo(ArtistRef seed) {
        return new Reason(Kind.SIMILAR_TO_ARTIST, seed.id(), seed.name(), null);
    }

    public static Reason sharedTag(String tag) {
        return new Reason(Kind.SHARED_TAG, null, null, tag);
    }

    public static Reason popular() {
        return new Reason(Kind.POPULAR, null, null, null);
    }
}
