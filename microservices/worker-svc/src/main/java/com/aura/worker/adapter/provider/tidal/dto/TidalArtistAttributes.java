package com.aura.worker.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TidalArtistAttributes(String name, Double popularity) {
}
