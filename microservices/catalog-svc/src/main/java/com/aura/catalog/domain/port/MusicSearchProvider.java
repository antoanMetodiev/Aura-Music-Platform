package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.SearchType;

import java.util.Set;

/** Outbound port for provider-side search (Project-Info.md §36). Separate from metadata so they can diverge later. */
public interface MusicSearchProvider {

    Provider provider();

    ProviderSearchResult search(String query, Set<SearchType> types, int limit);
}
