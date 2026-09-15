package com.aura.playback.domain.port;

import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.port.VideoHintStore.HintSource;

import java.util.List;

/**
 * Outbound port: a third party that may already know which YouTube video a recording is
 * (Project-Info.md §36: business code never talks to a specific provider). Implementations are
 * consulted in order — MusicBrainz (by ISRC), then Discogs (by artist + title) — until one has a link.
 */
public interface KnownVideoLookup {

    enum Outcome {
        /** At least one YouTube link is known for this recording. */
        LINK_FOUND,
        /** The recording exists but has no YouTube link. */
        NO_LINK,
        /** The recording isn't in the provider at all. */
        NOT_FOUND
    }

    /**
     * One link the provider knows. {@code title}/{@code durationMs} are what the provider says about the
     * video (nullable / 0 when it only has the id) — enough to rank several links before spending a
     * YouTube unit on the best one.
     */
    record KnownVideo(String youtubeVideoId, String title, long durationMs) {
        public static KnownVideo idOnly(String id) { return new KnownVideo(id, null, 0L); }
    }

    record Result(Outcome outcome, List<KnownVideo> videos) {
        public static Result found(List<KnownVideo> videos) { return new Result(Outcome.LINK_FOUND, List.copyOf(videos)); }
        public static Result found(String id) { return found(List.of(KnownVideo.idOnly(id))); }
        public static Result noLink() { return new Result(Outcome.NO_LINK, List.of()); }
        public static Result notFound() { return new Result(Outcome.NOT_FOUND, List.of()); }
    }

    /** Recorded on the hint so we can tell where an answer came from. */
    HintSource source();

    /** Whether this provider can be asked about the track at all (MusicBrainz needs an ISRC, Discogs doesn't). */
    boolean supports(CanonicalTrack track);

    /** @throws com.aura.playback.domain.service.PlaybackProviderUnavailableException when the provider can't answer right now */
    Result lookup(CanonicalTrack track);
}
