package com.aura.worker.adapter.provider.lastfm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * {@code artist.getSimilar}. Last.fm answers errors as 200 with {@code {error, message}} as often as
 * with a 4xx, so both fields are part of every response shape here.
 * {@code match} arrives as a string ("0.9999") — parsed, not bound.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LastFmSimilarResponse(SimilarArtists similarartists, Integer error, String message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SimilarArtists(List<SimilarArtist> artist) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SimilarArtist(String name, String mbid, String match, String url) {
    }
}
