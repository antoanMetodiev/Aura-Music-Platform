package com.aura.catalog.adapter.provider.noop;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.MusicMetadataProvider;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registered only when {@code music.providers.tidal.enabled=false} (an ops kill-switch, e.g. during
 * a TIDAL outage or a compliance pause — Project-Info.md §37). {@link com.aura.catalog.domain.service.CatalogService}
 * requires a {@link MusicMetadataProvider} bean to exist; with no active provider this one reports
 * every lookup as "not found" rather than failing the whole service to boot. Already-cached rows in
 * our own catalog stay fully readable — only new discovery and TTL refresh are affected.
 *
 * Paired with {@link NoopMusicSearchProvider} on the exact same (inverted) condition, so exactly one
 * implementation of each port is ever active — no {@code @ConditionalOnMissingBean} ordering risk.
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "false")
public class NoopMusicMetadataProvider implements MusicMetadataProvider {

    @Override
    public Provider provider() {
        return null;
    }

    @Override
    public Optional<ProviderTrack> getTrack(String providerResourceId) {
        return Optional.empty();
    }

    @Override
    public Optional<ProviderAlbum> getAlbum(String providerResourceId) {
        return Optional.empty();
    }

    @Override
    public List<ProviderTrack> getAlbumTracks(String providerResourceId) {
        return List.of();
    }

    @Override
    public Optional<ProviderArtist> getArtist(String providerResourceId) {
        return Optional.empty();
    }

    @Override
    public List<ProviderTrack> getArtistTracks(String providerResourceId) {
        return List.of();
    }

    @Override
    public List<ProviderTrack> findTracksByIsrc(String isrc) {
        return List.of();
    }
}
