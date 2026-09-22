package com.aura.worker.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Our side of the discography contract with catalog-svc. A deliberate copy of the shapes that
 * service publishes rather than a shared module: the two services are separately deployable, so the
 * contract is a thing they agree on, not a class they both compile against (Project-Info.md §51).
 */
public final class DiscographyWork {

    private DiscographyWork() {
    }

    /** An artist somebody has open right now — the only kind that is ever claimed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(UUID artistId, String name, String provider, String providerResourceId) {
    }

    public record Ref(String provider, String providerResourceId) {
    }

    public record Artwork(String url, int width, int height) {
    }

    public record Artist(Ref ref, String name, Artwork artwork, double popularity) {
    }

    public record Album(Ref ref, String title, String type, LocalDate releaseDate, Artist artist,
                        Artwork artwork, boolean explicit, int numberOfTracks, double popularity) {
    }

    public record Track(Ref ref, String title, String version, long durationMs, String isrc, boolean explicit,
                        double popularity, Album album, List<Artist> artists, Integer volumeNumber, Integer trackNumber) {
    }

    public record Ingest(List<Track> tracks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IngestResult(UUID artistId, String name, int trackCount, int newArtists) {
    }

    public record Failure(String error) {
    }
}
