package com.aura.recommendation.adapter.provider.lastfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code artist.getTopTags}. {@code count} is their 0..100 weight. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LastFmTopTagsResponse(TopTags toptags, Integer error, String message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopTags(List<Tag> tag) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tag(String name, Integer count, String url) {
    }
}
