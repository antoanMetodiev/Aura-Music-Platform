package com.aura.recommendation.domain.service;

import com.aura.recommendation.config.RecommendationProperties;
import com.aura.recommendation.domain.model.CandidateOrigin;
import com.aura.recommendation.domain.model.Reason;
import com.aura.recommendation.domain.model.RecommendedTrack;
import com.aura.recommendation.domain.model.TrackRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns candidate tracks into the feed: a deterministic weighted score, then the two rules that
 * decide whether a feed feels good rather than merely correct.
 *
 * <ol>
 *   <li><b>Playability.</b> A track playback-svc cannot play is dropped (§18's spirit: unavailable
 *       beats wrong, and unplayable-but-recommended is both). When playback-svc is down nothing can
 *       be checked, so nothing is dropped — a slightly optimistic feed beats an empty one (§48).</li>
 *   <li><b>Diversity.</b> Each further track by the same artist keeps only {@code perArtistDecay} of
 *       its score, and no artist may exceed {@code maxTracksPerArtist}. Without this every radio is
 *       the seed's own greatest hits: they always score highest, and they are the last thing someone
 *       asking for recommendations wants.</li>
 * </ol>
 *
 * <p>The same recording reaching the feed twice (a single and its album release, a collaboration
 * proposed through both artists) collapses to one entry — same ISRC, or same normalized title by the
 * same primary artist, exactly the rule catalog-svc uses for its own suggestions.
 */
@Component
public class TrackRanker {

    /** A track in the running, with the weight of the artist that proposed it. */
    public record Candidate(TrackRef track, double artistScore, UUID candidateArtistId, CandidateOrigin origin,
                            UUID seedArtistId, String seedArtistName, String tag) {
    }

    private final RecommendationProperties properties;

    public TrackRanker(RecommendationProperties properties) {
        this.properties = properties;
    }

    /**
     * @param playable        track ids playback-svc has a verified source for
     * @param playabilityKnown false when playback-svc could not be asked — nothing is dropped then
     * @param excludedTrackIds tracks the caller does not want back (the seed track of a track radio)
     */
    public List<RecommendedTrack> rank(List<Candidate> candidates, Set<UUID> playable, boolean playabilityKnown,
                                       Set<UUID> excludedTrackIds, int limit) {
        Map<UUID, Scored> best = new HashMap<>();
        Set<String> seenRecordings = new HashSet<>();

        for (Candidate candidate : candidates) {
            TrackRef track = candidate.track();
            if (track == null || track.id() == null) continue;
            if (excludedTrackIds.contains(track.id())) continue;

            boolean isPlayable = playable.contains(track.id());
            if (playabilityKnown && properties.dropUnplayable() && !isPlayable) continue;

            double score = candidate.artistScore() * properties.artistScoreWeight()
                    + track.popularity() * properties.trackPopularityWeight()
                    + (isPlayable ? properties.playableBonus() : 0);

            // The same track can be proposed by several candidate artists (a feature credit); keep the
            // strongest proposal rather than letting it in twice.
            Scored incoming = new Scored(track, score, candidate, isPlayable);
            best.merge(track.id(), incoming, (a, b) -> a.score() >= b.score() ? a : b);
        }

        List<Scored> ordered = new ArrayList<>(best.values());
        ordered.sort(Comparator.comparingDouble(Scored::score).reversed());

        Map<UUID, Integer> perArtist = new HashMap<>();
        List<Scored> kept = new ArrayList<>();
        for (Scored scored : ordered) {
            if (!seenRecordings.add(recordingKey(scored.track()))) continue;
            UUID artistId = scored.candidate().candidateArtistId();
            int taken = perArtist.getOrDefault(artistId, 0);
            if (taken >= properties.maxTracksPerArtist()) continue;
            perArtist.put(artistId, taken + 1);
            kept.add(scored.withScore(scored.score() * Math.pow(properties.perArtistDecay(), taken)));
        }

        // Re-sorted after the decay: the second track of a very strong artist may now rank below the
        // first track of a weaker one, which is the whole point of the decay.
        kept.sort(Comparator.comparingDouble(Scored::score).reversed());
        return kept.stream()
                .limit(limit)
                .map(s -> new RecommendedTrack(s.track(), round(s.score()), reasonFor(s), s.playable()))
                .toList();
    }

    private static Reason reasonFor(Scored scored) {
        Candidate candidate = scored.candidate();
        return switch (candidate.origin()) {
            case SEED -> new Reason(Reason.Kind.BY_SEED_ARTIST, candidate.seedArtistId(), candidate.seedArtistName(), null);
            case SIMILAR, REVERSE_SIMILAR ->
                    new Reason(Reason.Kind.SIMILAR_TO_ARTIST, candidate.seedArtistId(), candidate.seedArtistName(), null);
            case SHARED_TAG -> Reason.sharedTag(candidate.tag());
            case POPULAR -> Reason.popular();
        };
    }

    /**
     * Same ISRC, or same normalized title by the same primary artist — a re-release, a deluxe edition
     * and a compilation entry are one song to a listener.
     */
    private static String recordingKey(TrackRef track) {
        if (track.isrc() != null && !track.isrc().isBlank()) {
            return "i:" + track.isrc().trim().toUpperCase(Locale.ROOT);
        }
        String artist = track.primaryArtist() == null ? "" : String.valueOf(track.primaryArtist().id());
        return "t:" + track.title().trim().toLowerCase(Locale.ROOT) + "|" + artist;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Scored(TrackRef track, double score, Candidate candidate, boolean playable) {
        Scored withScore(double newScore) {
            return new Scored(track, newScore, candidate, playable);
        }
    }
}
