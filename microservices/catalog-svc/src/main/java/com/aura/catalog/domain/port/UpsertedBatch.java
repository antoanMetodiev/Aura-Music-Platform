package com.aura.catalog.domain.port;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Track;

import java.util.List;

/** What {@link CatalogStore#upsertBatch} hands back — each list in the same order as its input. */
public record UpsertedBatch(List<Track> tracks, List<Album> albums, List<Artist> artists) {
}
