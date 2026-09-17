package com.aura.worker.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TidalAlbumAttributes(
        String title,
        String albumType,
        String releaseDate,
        Boolean explicit,
        Integer numberOfItems,
        Double popularity,
        String barcodeId
) {
}
