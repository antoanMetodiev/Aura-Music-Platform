package com.aura.playback.adapter.provider.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeSnippet(
        String title,
        String description,
        @JsonProperty("channelId") String channelId,
        @JsonProperty("channelTitle") String channelTitle,
        @JsonProperty("categoryId") String categoryId
) {
}
