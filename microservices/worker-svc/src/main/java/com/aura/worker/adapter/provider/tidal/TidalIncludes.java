package com.aura.worker.adapter.provider.tidal;

import java.util.Set;

import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_ALBUMS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_ARTISTS;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_COVER_ART;
import static com.aura.worker.adapter.provider.tidal.TidalMapper.REL_PROFILE_ART;

/**
 * `include` sets shared by every TIDAL call that needs a fully-hydrated resource — enough
 * relationships to build a complete canonical Track/Album/Artist in one round trip.
 * TIDAL's documented `include` support is top-level only (no nested dot-paths confirmed), so
 * search results are hydrated with a second batched fetch using these same sets
 * (see {@link TidalSearchProvider}) rather than relying on nested includes.
 */
final class TidalIncludes {

    static final Set<String> TRACK = Set.of(REL_ALBUMS, REL_ARTISTS);
    static final Set<String> ALBUM = Set.of(REL_ARTISTS, REL_COVER_ART);
    static final Set<String> ARTIST = Set.of(REL_PROFILE_ART);

    private TidalIncludes() {
    }
}
