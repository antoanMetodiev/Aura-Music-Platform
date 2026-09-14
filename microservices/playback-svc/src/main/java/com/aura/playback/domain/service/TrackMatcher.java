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
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic candidate scoring (Project-Info.md §17, §18). Never picks a result just because
 * it's the only one — {@code PlaybackResolverService} is the one that turns a score into an
 * accept/candidate/reject decision against {@link MatchingProperties}'s thresholds; this class only
 * scores and ranks.
 *
 * <p>The song title is the one signal that must be present: a candidate whose title doesn't mention
 * the track (exactly or fuzzily) takes {@code missingTitlePenalty}, so no amount of "right artist,
 * right channel, says Official Video" can make a different song by the same artist a match.
 *
 * <p>A candidate that YouTube itself reports as non-embeddable is dropped before scoring — no score
 * makes an unplayable video usable (Project-Info.md §38: playback must go through the official
 * player, which requires embeddability).
 */
@Component
public class TrackMatcher {

    /** Levenshtein similarity at or above this counts as the same title with spelling/transliteration noise. */
    private static final double FUZZY_TITLE_THRESHOLD = 0.8;

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

        score += switch (matchTitle(track, normTrackTitle, normCandidateTitle)) {
            case EXACT -> weights.exactTitleMatch();
            case FUZZY -> weights.fuzzyTitleMatch();
            case NONE -> weights.missingTitlePenalty();
        };

        if (isTrustedChannel(candidate.channelTitle(), track.primaryArtist())) {
            score += weights.officialChannelBonus();
        } else if (isLabelChannel(candidate.channelTitle())) {
            score += weights.labelChannelBonus();
        }
        if (!normArtist.isBlank() && (normChannel.contains(normArtist) || normCandidateTitle.contains(normArtist))) {
            score += weights.exactArtistMatch();
        }

        if (track.durationMs() > 0 && candidate.durationMs() > 0) {
            long diffMs = Math.abs(candidate.durationMs() - track.durationMs());
            if (diffMs <= weights.durationTightToleranceMs()) {
                score += weights.durationTightBonus();
            } else if (diffMs <= weights.durationLooseToleranceMs()) {
                score += weights.durationLooseBonus();
            } else if (diffMs > weights.durationMismatchToleranceMs()) {
                score += weights.durationMismatchPenalty();
            }
        }

        // Keywords are checked on the raw lowercase title (brackets intact — that's where "(Official
        // Audio)" lives) and on the transliterated one, so Bulgarian "(На живо)" / "Концерт" / "Кавър"
        // count the same as their English equivalents.
        Keywords kw = new Keywords(rawTitleLower, transliterate(rawTitleLower));

        // "Official Audio" is the strongest marker; any other "Official …" (Video, HD Video, 4K, Lyric
        // Video, or just "(Official)") is the label/artist vouching for the upload.
        if (kw.any("official audio")) score += weights.officialAudioKeyword();
        else if (kw.any("official")) score += weights.officialMusicVideoKeyword();

        if (kw.any("live", "concert", "kontsert", "na zhivo")) score += weights.liveKeyword();
        if (kw.any("cover", "kavar")) score += weights.coverKeyword();
        if (kw.any("remix", "remiks")) score += weights.remixKeyword();
        if (kw.any("slowed")) score += weights.slowedKeyword();
        if (kw.any("reverb")) score += weights.reverbKeyword();
        if (kw.any("karaoke")) score += weights.karaokeKeyword();
        if (kw.any("instrumental")) score += weights.instrumentalKeyword();
        if (kw.any("nightcore")) score += weights.nightcoreKeyword();
        if (kw.any("acoustic", "akustichna", "akustichen")) score += weights.acousticKeyword();
        if (kw.any("sped up", "speed up")) score += weights.spedUpKeyword();
        if (kw.any("compilation")) score += weights.compilationKeyword();
        if (kw.any("mashup")) score += weights.mashupKeyword();
        if (kw.any("reaction", "reaktsiya")) score += weights.reactionKeyword();

        return score;
    }

    private record Keywords(String raw, String transliterated) {
        boolean any(String... phrases) {
            for (String phrase : phrases) {
                if (containsPhrase(raw, phrase) || containsPhrase(transliterated, phrase)) return true;
            }
            return false;
        }
    }

    private enum TitleMatch { EXACT, FUZZY, NONE }

    /**
     * Exact = the normalized track title appears in the normalized candidate title. Fuzzy = once every
     * artist name is stripped from the candidate title, what's left is within edit-distance noise of
     * the track title (different transliteration of the same Cyrillic word, a typo, an extra "the").
     */
    private static TitleMatch matchTitle(CanonicalTrack track, String normTrackTitle, String normCandidateTitle) {
        if (normTrackTitle.isBlank() || normCandidateTitle.isBlank()) return TitleMatch.NONE;
        if (normCandidateTitle.contains(normTrackTitle)) return TitleMatch.EXACT;

        String remainder = normCandidateTitle;
        for (String artist : track.artistNames()) {
            String normArtist = normalize(artist);
            if (!normArtist.isBlank()) remainder = remainder.replace(normArtist, " ");
        }
        remainder = remainder.replaceAll("\\s+", " ").trim();
        if (remainder.isEmpty()) return TitleMatch.NONE;

        return similarity(normTrackTitle, remainder) >= FUZZY_TITLE_THRESHOLD ? TitleMatch.FUZZY : TitleMatch.NONE;
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

    /** A configured label channel, or one whose name says so ("Virginia Records", "Monte Music Records"). */
    private boolean isLabelChannel(String channelTitle) {
        if (channelTitle == null || channelTitle.isBlank()) return false;
        String lower = channelTitle.toLowerCase(Locale.ROOT);
        if (weights.trustedLabelChannels().stream().anyMatch(t -> t.equalsIgnoreCase(channelTitle))) return true;
        return containsPhrase(lower, "records") || containsPhrase(lower, "recordings");
    }

    private static boolean containsPhrase(String haystack, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(haystack).find();
    }

    /**
     * Lowercase, Cyrillic transliterated to Latin, diacritic-stripped, bracket/punctuation-free — for
     * containment comparisons only. Transliteration is what lets TIDAL's "Obeshtavam" meet YouTube's
     * "Обещавам"; both sides go through the same table so the exact scheme doesn't matter, only that
     * it is applied consistently.
     */
    private static String normalize(String value) {
        if (value == null) return "";
        String withoutBrackets = value.replaceAll("[\\(\\[].*?[\\)\\]]", " ");
        String latin = transliterate(withoutBrackets.toLowerCase(Locale.ROOT));
        String deAccented = Normalizer.normalize(latin, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return deAccented.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Bulgarian streamlined system, plus the handful of Russian/Ukrainian letters that show up in titles. */
    private static final Map<Character, String> CYRILLIC_TO_LATIN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"),
            Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"), Map.entry('ш', "sh"),
            Map.entry('щ', "sht"), Map.entry('ъ', "a"), Map.entry('ь', "y"), Map.entry('ю', "yu"), Map.entry('я', "ya"),
            Map.entry('ё', "yo"), Map.entry('э', "e"), Map.entry('ы', "y"), Map.entry('є', "ye"), Map.entry('і', "i"),
            Map.entry('ї', "yi"), Map.entry('ґ', "g")
    );

    private static String transliterate(String lower) {
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String mapped = CYRILLIC_TO_LATIN.get(c);
            if (mapped != null) out.append(mapped);
            else out.append(c);
        }
        return out.toString();
    }

    /** 1.0 = identical, 0.0 = nothing in common (Levenshtein distance over the longer length). */
    private static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
