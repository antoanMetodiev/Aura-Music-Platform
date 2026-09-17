package com.aura.worker.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** TIDAL `Artworks_Attributes`: several renditions of the same image. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TidalArtworkAttributes(List<File> files, String mediaType) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record File(String href, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(int width, int height) {
    }
}
