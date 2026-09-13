package com.aura.catalog.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of TIDAL `Tracks_Attributes` we care about. `duration` is ISO-8601 (e.g. PT3M30S). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TidalTrackAttributes(
        String title,
        String version,
        String duration,
        String isrc,
        Boolean explicit,
        Double popularity
) {
}
