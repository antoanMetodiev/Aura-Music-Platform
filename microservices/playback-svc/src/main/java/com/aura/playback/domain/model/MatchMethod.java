package com.aura.playback.domain.model;

/** How a {@link PlaybackSource} was chosen (Project-Info.md §19: must be able to explain "how"). */
public enum MatchMethod {
    /** Score cleared the high-confidence threshold — trusted without a human looking at it. */
    AUTOMATIC,
    /** Score cleared the medium threshold only — stored as a candidate pending manual verification. */
    CANDIDATE,
    /** A human picked this source explicitly, overriding whatever the matcher found. */
    MANUAL,
    /**
     * Copied from another track with the same ISRC whose source was already verified — same recording,
     * so the same video, without a search or a validation call of its own.
     */
    ISRC_SIBLING,
    /**
     * We searched and found nothing above even the medium threshold. Stored (with a null
     * {@code providerResourceId}) purely so a repeat request doesn't burn YouTube quota re-running
     * a search we already know fails (Project-Info.md §20).
     */
    NONE
}
