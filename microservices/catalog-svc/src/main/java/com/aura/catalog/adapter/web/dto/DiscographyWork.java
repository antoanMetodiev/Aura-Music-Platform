package com.aura.catalog.adapter.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The wire contract between catalog-svc and worker-svc for the discography sync.
 *
 * <p>The work is split down the middle: worker-svc owns the provider credentials, the pacing and the
 * fetching; catalog-svc owns the queue and every row in {@code catalog.*} (Project-Info.md §6). So
 * the worker claims an artist, goes to TIDAL on its own key, and posts back what it found — it never
 * touches our tables, and we never touch its rate limit.
 *
 * <p>These shapes deliberately mirror the domain's {@code ProviderTrack} family rather than reusing
 * it: the contract is what the two services agreed on, and it should not change just because a
 * domain record grows a field.
 */
public final class DiscographyWork {

    private DiscographyWork() {
    }

    /** An artist claimed for syncing. The claim is already stamped, so nobody else will take it. */
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

    /** What the worker posts back after a successful fetch. */
    public record Ingest(List<Track> tracks) {
    }

    /**
     * @param newArtists artists this batch introduced that we had never seen — featured artists and
     *                   album artists the catalog now knows about, which is how it keeps growing
     */
    public record IngestResult(UUID artistId, String name, int trackCount, int newArtists) {
    }

    public record Failure(String error) {
    }
}
