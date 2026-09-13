package com.aura.catalog.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum SearchType {
    TRACKS, ALBUMS, ARTISTS;

    public static final Set<SearchType> ALL = EnumSet.allOf(SearchType.class);
}
