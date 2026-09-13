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
        @DefaultValue("25") int exactArtistMatch,
        @DefaultValue("25") int exactTitleMatch,
        @DefaultValue("20") int durationWithin1SecBonus,
        @DefaultValue("10") int durationWithin3SecBonus,
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
