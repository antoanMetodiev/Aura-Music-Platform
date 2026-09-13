package com.aura.playback.domain.service;

import com.aura.playback.config.MatchingProperties;
import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.ScoredCandidate;
import com.aura.playback.domain.model.VideoCandidate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic candidate scoring (Project-Info.md §17, §18). Never picks a result just because
 * it's the only one — {@code PlaybackResolverService} is the one that turns a score into an
 * accept/candidate/reject decision against {@link MatchingProperties}'s thresholds; this class only
 * scores and ranks.
 *
 * <p>A candidate that YouTube itself reports as non-embeddable is dropped before scoring — no score
 * makes an unplayable video usable (Project-Info.md §38: playback must go through the official
 * player, which requires embeddability).
 */
@Component
public class TrackMatcher {

    private final MatchingProperties weights;

    public TrackMatcher(MatchingProperties weights) {
        this.weights = weights;
    }

    /** Highest score first. Candidates YouTube marked non-embeddable are excluded entirely. */
    public List<ScoredCandidate> rank(CanonicalTrack track, List<VideoCandidate> candidates) {
        return candidates.stream()
                .filter(VideoCandidate::embeddable)
                .map(candidate -> new ScoredCandidate(candidate, score(track, candidate)))
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .toList();
    }

    private int score(CanonicalTrack track, VideoCandidate candidate) {
        int score = 0;

        String normCandidateTitle = normalize(candidate.title());
        String normTrackTitle = normalize(track.title());
        String normArtist = normalize(track.primaryArtist());
        String normChannel = normalize(candidate.channelTitle());
        String rawTitleLower = candidate.title() == null ? "" : candidate.title().toLowerCase(Locale.ROOT);

        if (isTrustedChannel(candidate.channelTitle(), track.primaryArtist())) {
            score += weights.officialChannelBonus();
        }
        if (!normArtist.isBlank() && (normChannel.contains(normArtist) || normCandidateTitle.contains(normArtist))) {
            score += weights.exactArtistMatch();
        }
        if (!normTrackTitle.isBlank() && normCandidateTitle.contains(normTrackTitle)) {
            score += weights.exactTitleMatch();
        }

        if (track.durationMs() > 0 && candidate.durationMs() > 0) {
            long diffMs = Math.abs(candidate.durationMs() - track.durationMs());
            if (diffMs <= 1000) {
                score += weights.durationWithin1SecBonus();
            } else if (diffMs <= 3000) {
                score += weights.durationWithin3SecBonus();
            }
        }

        if (containsPhrase(rawTitleLower, "official audio")) score += weights.officialAudioKeyword();
        if (containsPhrase(rawTitleLower, "official music video") || containsPhrase(rawTitleLower, "official video")) {
            score += weights.officialMusicVideoKeyword();
        }

        if (containsPhrase(rawTitleLower, "live")) score += weights.liveKeyword();
        if (containsPhrase(rawTitleLower, "cover")) score += weights.coverKeyword();
        if (containsPhrase(rawTitleLower, "remix")) score += weights.remixKeyword();
        if (containsPhrase(rawTitleLower, "slowed")) score += weights.slowedKeyword();
        if (containsPhrase(rawTitleLower, "reverb")) score += weights.reverbKeyword();
        if (containsPhrase(rawTitleLower, "karaoke")) score += weights.karaokeKeyword();
        if (containsPhrase(rawTitleLower, "instrumental")) score += weights.instrumentalKeyword();
        if (containsPhrase(rawTitleLower, "nightcore")) score += weights.nightcoreKeyword();
        if (containsPhrase(rawTitleLower, "acoustic")) score += weights.acousticKeyword();
        if (containsPhrase(rawTitleLower, "sped up") || containsPhrase(rawTitleLower, "speed up")) {
            score += weights.spedUpKeyword();
        }
        if (containsPhrase(rawTitleLower, "compilation")) score += weights.compilationKeyword();
        if (containsPhrase(rawTitleLower, "mashup")) score += weights.mashupKeyword();
        if (containsPhrase(rawTitleLower, "reaction")) score += weights.reactionKeyword();

        return score;
    }

    /**
     * A channel we trust enough to count as "official" without a channel allowlist: YouTube's
     * auto-generated {@code " - Topic"} channel for a rightsholder's official audio uploads, a
     * VEVO channel, or a channel whose name is simply the artist's name.
     */
    private boolean isTrustedChannel(String channelTitle, String artist) {
        if (channelTitle == null || channelTitle.isBlank()) return false;
        String lower = channelTitle.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" - topic")) return true;
        if (lower.endsWith("vevo")) return true;
        String normChannel = normalize(channelTitle);
        String normArtist = normalize(artist);
        return !normArtist.isBlank() && normChannel.equals(normArtist);
    }

    private static boolean containsPhrase(String haystack, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(haystack).find();
    }

    /** Lowercase, diacritic-stripped, bracket/punctuation-free — for containment comparisons only. */
    private static String normalize(String value) {
        if (value == null) return "";
        String withoutBrackets = value.replaceAll("[\\(\\[].*?[\\)\\]]", " ");
        String deAccented = Normalizer.normalize(withoutBrackets.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return deAccented.replaceAll("[^a-z0-9]+", " ").trim();
    }
}
