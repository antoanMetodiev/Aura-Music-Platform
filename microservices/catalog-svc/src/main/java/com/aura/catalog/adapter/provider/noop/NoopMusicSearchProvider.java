package com.aura.catalog.adapter.provider.noop;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.port.MusicSearchProvider;
import com.aura.catalog.domain.port.ProviderSearchResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;

/** See {@link NoopMusicMetadataProvider} — same kill-switch, search side. */
@Component
@ConditionalOnProperty(prefix = "music.providers.tidal", name = "enabled", havingValue = "false")
public class NoopMusicSearchProvider implements MusicSearchProvider {

    @Override
    public Provider provider() {
        return null;
    }

    @Override
    public ProviderSearchResult search(String query, Set<SearchType> types, int limit) {
        return ProviderSearchResult.empty();
    }
}
