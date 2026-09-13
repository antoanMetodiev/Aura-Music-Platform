package com.aura.playback.adapter.provider.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** GET /youtube/v3/videos response (only the fields we read). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeVideosResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, YouTubeSnippet snippet, ContentDetails contentDetails, Status status) {
    }

    /** {@code duration} is an ISO-8601 duration (e.g. {@code PT3M33S}) — parse with {@link java.time.Duration#parse}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentDetails(String duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(boolean embeddable) {
    }
}
