package com.aura.playback.domain.port;

import java.util.Optional;

/**
 * Outbound port: a third party that may already know which YouTube video a recording is
 * (Project-Info.md §36: business code never talks to a specific provider). One implementation today
 * — MusicBrainz, keyed by ISRC.
 */
public interface KnownVideoLookup {

    enum Outcome {
        /** A YouTube link is known for this recording. */
        LINK_FOUND,
        /** The recording exists but has no YouTube link. */
        NO_LINK,
        /** The recording isn't in the provider at all. */
        NOT_FOUND
    }

    record Result(Outcome outcome, Optional<String> youtubeVideoId) {
        public static Result found(String id) { return new Result(Outcome.LINK_FOUND, Optional.of(id)); }
        public static Result noLink() { return new Result(Outcome.NO_LINK, Optional.empty()); }
        public static Result notFound() { return new Result(Outcome.NOT_FOUND, Optional.empty()); }
    }

    /** @throws com.aura.playback.domain.service.PlaybackProviderUnavailableException when the provider can't answer right now */
    Result lookupByIsrc(String isrc);
}
