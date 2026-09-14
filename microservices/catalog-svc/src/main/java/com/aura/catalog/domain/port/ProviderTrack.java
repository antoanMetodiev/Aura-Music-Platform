package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.ProviderReference;

import java.util.List;

/**
 * {@code volumeNumber}/{@code trackNumber} are only known when the track came from its album's
 * item list — a track found via search or a direct fetch carries {@code null} for both.
 */
public record ProviderTrack(
        ProviderReference ref,
        String title,
        String version,
        long durationMs,
        String isrc,
        boolean explicit,
        double popularity,
        ProviderAlbum album,
        List<ProviderArtist> artists,
        Integer volumeNumber,
        Integer trackNumber
) {
    public ProviderTrack withPosition(Integer volumeNumber, Integer trackNumber) {
        return new ProviderTrack(ref, title, version, durationMs, isrc, explicit, popularity, album, artists,
                volumeNumber, trackNumber);
    }
}
