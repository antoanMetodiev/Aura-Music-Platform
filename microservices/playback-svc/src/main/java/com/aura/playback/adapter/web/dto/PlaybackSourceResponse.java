package com.aura.playback.adapter.web.dto;

import java.util.UUID;

/**
 * What the frontend player needs to drive YouTube's IFrame Player API against
 * {@code providerResourceId} (a YouTube video id) — Project-Info.md §38: the backend hands over the
 * video identifier only, never a direct audio URL.
 */
public record PlaybackSourceResponse(
        UUID trackId,
        String provider,
        String providerResourceId,
        String title,
        String channelTitle,
        long durationMs
) {
}
