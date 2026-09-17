package com.aura.catalog.adapter.web;

import com.aura.catalog.adapter.web.dto.DiscographyWork;
import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.DiscographySyncStore.PendingArtist;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.service.ArtistDiscographyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The work API worker-svc drives the discography sync through. Service-to-service only — it is not
 * routed through the gateway, and nothing a browser does reaches it.
 *
 * <p>Three calls make a unit of work: claim an artist, fetch it (elsewhere, on the worker's own
 * provider credentials), post back the result — or say it couldn't be fetched, in which case the
 * claim is undone and the artist keeps its place. catalog-svc stays the only writer of
 * {@code catalog.*} (Project-Info.md §6) while owning none of the provider's rate limit.
 */
@RestController
@RequestMapping("/api/v1/catalog/internal/discography")
public class DiscographyWorkController {

    private final ArtistDiscographyService service;

    public DiscographyWorkController(ArtistDiscographyService service) {
        this.service = service;
    }

    /** @return the claimed artist, or 204 when the whole catalog's discographies are fresh */
    @PostMapping("/claim")
    public ResponseEntity<DiscographyWork.Claim> claim() {
        return service.claimNext()
                .map(DiscographyWorkController::toClaim)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{artistId}/tracks")
    public DiscographyWork.IngestResult ingest(@PathVariable UUID artistId,
                                               @RequestBody DiscographyWork.Ingest body) {
        if (body == null || body.tracks() == null) {
            throw new IllegalArgumentException("'tracks' must be present (an empty list is fine)");
        }
        String name = nameOf(artistId);
        ArtistDiscographyService.Outcome outcome = service.ingest(artistId, name, body.tracks().stream()
                .map(DiscographyWorkController::toProviderTrack)
                .toList());
        return new DiscographyWork.IngestResult(outcome.artistId(), outcome.name(), outcome.trackCount(), outcome.newArtists());
    }

    /** The provider was unreachable — undo the claim, nothing is known about this artist. */
    @PostMapping("/{artistId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID artistId, @RequestBody(required = false) DiscographyWork.Failure body) {
        service.release(artistId, nameOf(artistId), body == null ? "provider unavailable" : body.error());
        return ResponseEntity.noContent().build();
    }

    /** The fetch failed for a reason about this artist — record it, retry after the configured delay. */
    @PostMapping("/{artistId}/failed")
    public ResponseEntity<Void> failed(@PathVariable UUID artistId, @RequestBody DiscographyWork.Failure body) {
        service.markFailed(artistId, nameOf(artistId), body == null ? "unknown" : body.error());
        return ResponseEntity.noContent().build();
    }

    /**
     * The worker knows the name — it was in the claim — but sending it back would let a caller
     * relabel an artist. Reading it here is one primary-key lookup and keeps the name ours.
     */
    private String nameOf(UUID artistId) {
        return service.nameOf(artistId);
    }

    private static DiscographyWork.Claim toClaim(PendingArtist artist) {
        return new DiscographyWork.Claim(artist.id(), artist.name(),
                artist.ref().provider().name(), artist.ref().providerResourceId());
    }

    // ── Wire → domain ──────────────────────────────────────────────────────────────────────

    private static ProviderTrack toProviderTrack(DiscographyWork.Track t) {
        return new ProviderTrack(toRef(t.ref()), t.title(), t.version(), t.durationMs(), t.isrc(), t.explicit(),
                t.popularity(), toProviderAlbum(t.album()),
                t.artists() == null ? List.of() : t.artists().stream().map(DiscographyWorkController::toProviderArtist).toList(),
                t.volumeNumber(), t.trackNumber());
    }

    private static ProviderAlbum toProviderAlbum(DiscographyWork.Album a) {
        if (a == null) return null;
        return new ProviderAlbum(toRef(a.ref()), a.title(), toAlbumType(a.type()), a.releaseDate(),
                toProviderArtist(a.artist()), toArtwork(a.artwork()), a.explicit(), a.numberOfTracks(), a.popularity());
    }

    private static ProviderArtist toProviderArtist(DiscographyWork.Artist a) {
        if (a == null) return null;
        return new ProviderArtist(toRef(a.ref()), a.name(), toArtwork(a.artwork()), a.popularity());
    }

    private static ProviderReference toRef(DiscographyWork.Ref ref) {
        if (ref == null) return null;
        return new ProviderReference(Provider.valueOf(ref.provider().toUpperCase(Locale.ROOT)), ref.providerResourceId());
    }

    private static Artwork toArtwork(DiscographyWork.Artwork a) {
        return a == null || a.url() == null ? null : new Artwork(a.url(), a.width(), a.height());
    }

    private static AlbumType toAlbumType(String value) {
        if (value == null) return AlbumType.UNKNOWN;
        try {
            return AlbumType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AlbumType.UNKNOWN;
        }
    }
}
