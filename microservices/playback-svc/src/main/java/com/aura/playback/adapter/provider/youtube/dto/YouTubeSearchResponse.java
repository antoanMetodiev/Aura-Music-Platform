package com.aura.playback.adapter.provider.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** GET /youtube/v3/search response (only the fields we read). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeSearchResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Id id, YouTubeSnippet snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Id(String videoId) {
    }
}
