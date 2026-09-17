package com.aura.worker.domain.port;

import com.aura.worker.domain.model.Provider;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for external metadata (Project-Info.md §12, §15, §36).
 * Implementations live in {@code adapter.provider.*}; domain code only ever sees these normalized records.
 */
public interface MusicMetadataProvider {

    Provider provider();

    Optional<ProviderTrack> getTrack(String providerResourceId);

    Optional<ProviderAlbum> getAlbum(String providerResourceId);

    /** Every track on the album in play order, each carrying its volume/track number. Empty if the album is unknown. */
    List<ProviderTrack> getAlbumTracks(String providerResourceId);

    Optional<ProviderArtist> getArtist(String providerResourceId);

    /** Every track the artist appears on (own releases and features). Empty if the artist is unknown. */
    List<ProviderTrack> getArtistTracks(String providerResourceId);

    /**
     * {@code quick} fetches only what an artist page needs — one entry per distinct recording, the
     * first pages of it — for an artist somebody is waiting on. The full pull is what the catalog
     * wants; nobody should sit in front of a loading screen for it.
     */
    List<ProviderTrack> getArtistTracks(String providerResourceId, boolean quick);

    /** ISRC lookups can legitimately return several tracks (re-releases, regional variants). */
    List<ProviderTrack> findTracksByIsrc(String isrc);
}
