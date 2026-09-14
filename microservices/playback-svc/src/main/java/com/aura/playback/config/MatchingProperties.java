package com.aura.playback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Deterministic scoring weights and confidence thresholds for {@code TrackMatcher}
 * (Project-Info.md §18). Starting values match the doc's example configuration; tune here rather
 * than in code.
 *
 * <p>Two signals from the doc are narrowed to what the public YouTube Data API can actually tell
 * us: it never returns ISRC on a search result (that's a Content ID / partner-API field), so
 * {@code isrcExactMatch} is wired but will not fire against YouTube today; and it has no
 * "official channel" flag, so {@code officialChannelBonus} is a single tier covering what the doc
 * splits into "official artist channel" / "official label channel" — approximated by a
 * {@code " - Topic"} channel suffix (YouTube's auto-generated channel for a rightsholder's official
 * audio), a {@code VEVO} channel, or a channel name that is exactly the artist's name.
 */
@Validated
@ConfigurationProperties(prefix = "playback.matching")
public record MatchingProperties(
        @DefaultValue("100") int isrcExactMatch,
        @DefaultValue("40") int officialChannelBonus,
        /** Channel is a known label (see {@code trustedLabelChannels}) or reads like one ("… Records"). */
        @DefaultValue("30") int labelChannelBonus,
        /** Exact channel titles trusted as labels, case-insensitive. */
        @DefaultValue({}) java.util.List<String> trustedLabelChannels,
        @DefaultValue("25") int exactArtistMatch,
        @DefaultValue("25") int exactTitleMatch,
        /** Title differs only by transliteration/spelling noise ("Obestavam" vs "Obeshtavam"). */
        @DefaultValue("15") int fuzzyTitleMatch,
        /**
         * The candidate's title doesn't mention the song at all. Large enough that channel + artist +
         * keyword bonuses can never lift such a candidate to a confident match — the artist's own
         * channel uploading a *different* song used to win on exactly those signals.
         */
        @DefaultValue("-100") int missingTitlePenalty,
        /**
         * Duration windows are wider than the doc's 1s/3s example on purpose: provider durations are
         * rounded and real uploads of the same recording routinely differ by a few seconds. With the
         * title gate rejecting different songs outright, duration only has to separate *versions* of
         * the same song (radio edit vs extended), which differ by far more than this.
         */
        @DefaultValue("2000") long durationTightToleranceMs,
        @DefaultValue("20") int durationTightBonus,
        @DefaultValue("15000") long durationLooseToleranceMs,
        @DefaultValue("10") int durationLooseBonus,
        /** Past this the candidate is a different *version* (live set, extended mix, sped up) even if the title matches. */
        @DefaultValue("20000") long durationMismatchToleranceMs,
        @DefaultValue("-40") int durationMismatchPenalty,
        @DefaultValue("15") int officialAudioKeyword,
        @DefaultValue("10") int officialMusicVideoKeyword,

        @DefaultValue("-40") int liveKeyword,
        @DefaultValue("-50") int coverKeyword,
        @DefaultValue("-40") int remixKeyword,
        @DefaultValue("-40") int slowedKeyword,
        @DefaultValue("-30") int reverbKeyword,
        @DefaultValue("-50") int karaokeKeyword,
        @DefaultValue("-50") int instrumentalKeyword,
        @DefaultValue("-50") int nightcoreKeyword,
        @DefaultValue("-30") int acousticKeyword,
        @DefaultValue("-40") int spedUpKeyword,
        @DefaultValue("-30") int compilationKeyword,
        @DefaultValue("-30") int mashupKeyword,
        @DefaultValue("-100") int reactionKeyword,

        /** score >= this -> trusted automatic match ({@link com.aura.playback.domain.model.MatchMethod#AUTOMATIC}). */
        @DefaultValue("70") int highConfidenceThreshold,
        /** score >= this (but below high) -> stored as an unverified candidate, never auto-selected for playback. */
        @DefaultValue("40") int mediumConfidenceThreshold
) {
}
