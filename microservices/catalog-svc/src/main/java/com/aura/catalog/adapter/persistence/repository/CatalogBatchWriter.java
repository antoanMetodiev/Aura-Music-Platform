package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderArtist;
import com.aura.catalog.domain.port.ProviderTrack;
import com.aura.catalog.domain.port.UpsertedBatch;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Writes a whole provider result set (Project-Info.md §14 write-behind) in a fixed handful of
 * statements: one multi-row {@code INSERT ... ON CONFLICT (primary_ref) DO UPDATE} per entity table,
 * one multi-row {@code ON CONFLICT DO NOTHING} per refs table, and one delete + one insert for
 * track↔artist links. That's ~10 round trips for a 20-track search instead of ~20 per track, and —
 * because the conflict target is a unique key on the entity row itself (V3 migration) — two
 * concurrent writes of the same entity can never both insert, so no retry/fallback logic is needed.
 * Rows are written in {@code primary_ref} order so overlapping concurrent batches lock rows in the
 * same order and can't deadlock.
 */
@Repository
public class CatalogBatchWriter {

    private final JdbcClient jdbc;
    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;

    public CatalogBatchWriter(JdbcClient jdbc, ArtistRepository artists, AlbumRepository albums, TrackRepository tracks) {
        this.jdbc = jdbc;
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
    }

    @Transactional
    public UpsertedBatch upsert(List<ProviderTrack> trackSources, List<ProviderAlbum> albumSources, List<ProviderArtist> artistSources) {
        // Every artist any of the inputs references, deduplicated by provider key.
        Map<String, ProviderArtist> allArtists = new LinkedHashMap<>();
        artistSources.forEach(a -> allArtists.putIfAbsent(key(a.ref()), a));
        albumSources.forEach(al -> { if (al.artist() != null) allArtists.putIfAbsent(key(al.artist().ref()), al.artist()); });
        for (ProviderTrack t : trackSources) {
            if (t.album() != null && t.album().artist() != null) allArtists.putIfAbsent(key(t.album().artist().ref()), t.album().artist());
            t.artists().forEach(a -> allArtists.putIfAbsent(key(a.ref()), a));
        }
        Map<String, Artist> artistByKey = upsertArtists(allArtists.values());

        Map<String, ProviderAlbum> allAlbums = new LinkedHashMap<>();
        albumSources.forEach(al -> allAlbums.putIfAbsent(key(al.ref()), al));
        trackSources.forEach(t -> { if (t.album() != null) allAlbums.putIfAbsent(key(t.album().ref()), t.album()); });
        Map<String, Album> albumByKey = upsertAlbums(allAlbums.values(), artistByKey);

        Map<String, ProviderTrack> allTracks = new LinkedHashMap<>();
        trackSources.forEach(t -> allTracks.putIfAbsent(key(t.ref()), t));
        Map<String, Track> trackByKey = upsertTracks(allTracks.values(), albumByKey, artistByKey);

        return new UpsertedBatch(
                trackSources.stream().map(t -> trackByKey.get(key(t.ref()))).filter(Objects::nonNull).toList(),
                albumSources.stream().map(a -> albumByKey.get(key(a.ref()))).filter(Objects::nonNull).toList(),
                artistSources.stream().map(a -> artistByKey.get(key(a.ref()))).filter(Objects::nonNull).toList());
    }

    // ── Artists ────────────────────────────────────────────────────────────────────────────

    private Map<String, Artist> upsertArtists(Collection<ProviderArtist> sources) {
        if (sources.isEmpty()) return Map.of();
        List<ProviderArtist> sorted = sources.stream().sorted((a, b) -> key(a.ref()).compareTo(key(b.ref()))).toList();

        StringBuilder sql = new StringBuilder("""
                INSERT INTO catalog.artists
                    (id, primary_ref, name, artwork_url, artwork_width, artwork_height, popularity,
                     provider_synced_at, created_at, updated_at)
                VALUES """);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ProviderArtist a = sorted.get(i);
            Artwork art = a.artwork();
            sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, now(), now(), now())");
            params.add(UUID.randomUUID());
            params.add(key(a.ref()));
            params.add(a.name());
            params.add(art == null ? null : art.url());
            params.add(art == null ? null : art.width());
            params.add(art == null ? null : art.height());
            params.add(a.popularity());
        }
        sql.append("""

                ON CONFLICT (primary_ref) DO UPDATE SET
                    name = EXCLUDED.name,
                    -- Never trade a picture for nothing. The same artist arrives in several shapes:
                    -- a search result carries their photo, the artist nested inside a track does not,
                    -- and a discography sync writes hundreds of the latter. Overwriting blindly meant
                    -- an artist's hero image vanished the moment their catalogue was filled in.
                    artwork_url = COALESCE(EXCLUDED.artwork_url, catalog.artists.artwork_url),
                    artwork_width = CASE WHEN EXCLUDED.artwork_url IS NOT NULL
                                         THEN EXCLUDED.artwork_width ELSE catalog.artists.artwork_width END,
                    artwork_height = CASE WHEN EXCLUDED.artwork_url IS NOT NULL
                                          THEN EXCLUDED.artwork_height ELSE catalog.artists.artwork_height END,
                    popularity = EXCLUDED.popularity,
                    provider_synced_at = now(), updated_at = now()
                RETURNING *
                """);
        List<ArtistRepository.ArtistRow> rows = jdbc.sql(sql.toString()).params(params).query(ArtistRepository::mapRow).list();

        Map<String, UUID> idByKey = new HashMap<>();
        rows.forEach(r -> idByKey.put(r.primaryRef(), r.id()));
        insertRefs("catalog.artist_provider_refs", "artist_id", sorted, a -> idByKey.get(key(a.ref())), ProviderArtist::ref);

        Map<UUID, List<ProviderReference>> refs = artists.findRefsByIds(rows.stream().map(ArtistRepository.ArtistRow::id).toList());
        Map<String, Artist> result = new HashMap<>();
        rows.forEach(r -> result.put(r.primaryRef(), ArtistRepository.toArtist(r, refs.getOrDefault(r.id(), List.of()))));
        return result;
    }

    // ── Albums ─────────────────────────────────────────────────────────────────────────────

    private Map<String, Album> upsertAlbums(Collection<ProviderAlbum> sources, Map<String, Artist> artistByKey) {
        if (sources.isEmpty()) return Map.of();
        List<ProviderAlbum> sorted = sources.stream().sorted((a, b) -> key(a.ref()).compareTo(key(b.ref()))).toList();

        StringBuilder sql = new StringBuilder("""
                INSERT INTO catalog.albums
                    (id, primary_ref, title, album_type, release_date, artist_id, artwork_url, artwork_width,
                     artwork_height, explicit, number_of_tracks, popularity, provider_synced_at, created_at, updated_at)
                VALUES """);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ProviderAlbum al = sorted.get(i);
            Artwork art = al.artwork();
            Artist artist = al.artist() == null ? null : artistByKey.get(key(al.artist().ref()));
            sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), now())");
            params.add(UUID.randomUUID());
            params.add(key(al.ref()));
            params.add(al.title());
            params.add(al.type().name());
            params.add(al.releaseDate());
            params.add(artist == null ? null : artist.id());
            params.add(art == null ? null : art.url());
            params.add(art == null ? null : art.width());
            params.add(art == null ? null : art.height());
            params.add(al.explicit());
            params.add(al.numberOfTracks());
            params.add(al.popularity());
        }
        sql.append("""

                ON CONFLICT (primary_ref) DO UPDATE SET
                    title = EXCLUDED.title, album_type = EXCLUDED.album_type, release_date = EXCLUDED.release_date,
                    artist_id = EXCLUDED.artist_id,
                    -- Same rule as the artists above: a shape that doesn't carry the cover must not erase it.
                    artwork_url = COALESCE(EXCLUDED.artwork_url, catalog.albums.artwork_url),
                    artwork_width = CASE WHEN EXCLUDED.artwork_url IS NOT NULL
                                         THEN EXCLUDED.artwork_width ELSE catalog.albums.artwork_width END,
                    artwork_height = CASE WHEN EXCLUDED.artwork_url IS NOT NULL
                                          THEN EXCLUDED.artwork_height ELSE catalog.albums.artwork_height END,
                    explicit = EXCLUDED.explicit,
                    number_of_tracks = EXCLUDED.number_of_tracks, popularity = EXCLUDED.popularity,
                    provider_synced_at = now(), updated_at = now()
                RETURNING *
                """);
        List<AlbumRepository.AlbumRow> rows = jdbc.sql(sql.toString()).params(params).query(AlbumRepository::mapRow).list();

        Map<String, UUID> idByKey = new HashMap<>();
        rows.forEach(r -> idByKey.put(r.primaryRef(), r.id()));
        insertRefs("catalog.album_provider_refs", "album_id", sorted, al -> idByKey.get(key(al.ref())), ProviderAlbum::ref);

        Map<UUID, Artist> artistById = new HashMap<>();
        artistByKey.values().forEach(a -> artistById.put(a.id(), a));
        Map<UUID, List<ProviderReference>> refs = albums.findRefsByIds(rows.stream().map(AlbumRepository.AlbumRow::id).toList());
        Map<String, Album> result = new HashMap<>();
        for (AlbumRepository.AlbumRow r : rows) {
            Artist artist = r.artistId() == null ? null : artistById.get(r.artistId());
            result.put(r.primaryRef(), AlbumRepository.toAlbum(r, artist, refs.getOrDefault(r.id(), List.of())));
        }
        return result;
    }

    // ── Tracks ─────────────────────────────────────────────────────────────────────────────

    private Map<String, Track> upsertTracks(Collection<ProviderTrack> sources, Map<String, Album> albumByKey, Map<String, Artist> artistByKey) {
        if (sources.isEmpty()) return Map.of();
        List<ProviderTrack> sorted = sources.stream().sorted((a, b) -> key(a.ref()).compareTo(key(b.ref()))).toList();

        StringBuilder sql = new StringBuilder("""
                INSERT INTO catalog.tracks
                    (id, primary_ref, title, version, duration_ms, isrc, explicit, popularity, album_id,
                     volume_number, track_number, provider_synced_at, created_at, updated_at)
                VALUES """);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ProviderTrack t = sorted.get(i);
            Album album = t.album() == null ? null : albumByKey.get(key(t.album().ref()));
            sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), now())");
            params.add(UUID.randomUUID());
            params.add(key(t.ref()));
            params.add(t.title());
            params.add(t.version());
            params.add(t.durationMs());
            params.add(t.isrc());
            params.add(t.explicit());
            params.add(t.popularity());
            params.add(album == null ? null : album.id());
            params.add(t.volumeNumber());
            params.add(t.trackNumber());
        }
        // A track re-discovered via search carries no position; COALESCE keeps the one its album sync set.
        sql.append("""

                ON CONFLICT (primary_ref) DO UPDATE SET
                    title = EXCLUDED.title, version = EXCLUDED.version, duration_ms = EXCLUDED.duration_ms,
                    isrc = EXCLUDED.isrc, explicit = EXCLUDED.explicit, popularity = EXCLUDED.popularity,
                    album_id = EXCLUDED.album_id,
                    volume_number = COALESCE(EXCLUDED.volume_number, catalog.tracks.volume_number),
                    track_number = COALESCE(EXCLUDED.track_number, catalog.tracks.track_number),
                    provider_synced_at = now(), updated_at = now()
                RETURNING *
                """);
        List<TrackRepository.TrackRow> rows = jdbc.sql(sql.toString()).params(params).query(TrackRepository::mapRow).list();

        Map<String, UUID> idByKey = new HashMap<>();
        rows.forEach(r -> idByKey.put(r.primaryRef(), r.id()));
        insertRefs("catalog.track_provider_refs", "track_id", sorted, t -> idByKey.get(key(t.ref())), ProviderTrack::ref);
        replaceTrackArtists(sorted, idByKey, artistByKey);

        Map<UUID, Album> albumById = new HashMap<>();
        albumByKey.values().forEach(a -> albumById.put(a.id(), a));
        Map<UUID, List<ProviderReference>> refs = tracks.findRefsByIds(rows.stream().map(TrackRepository.TrackRow::id).toList());
        Map<String, Track> result = new HashMap<>();
        for (TrackRepository.TrackRow r : rows) {
            ProviderTrack source = sources.stream().filter(t -> key(t.ref()).equals(r.primaryRef())).findFirst().orElseThrow();
            List<Artist> trackArtists = source.artists().stream().map(a -> artistByKey.get(key(a.ref()))).filter(Objects::nonNull).toList();
            Album album = r.albumId() == null ? null : albumById.get(r.albumId());
            result.put(r.primaryRef(), TrackRepository.toTrack(r, album, trackArtists, refs.getOrDefault(r.id(), List.of())));
        }
        return result;
    }

    private void replaceTrackArtists(List<ProviderTrack> sorted, Map<String, UUID> trackIdByKey, Map<String, Artist> artistByKey) {
        List<UUID> trackIds = sorted.stream().map(t -> trackIdByKey.get(key(t.ref()))).filter(Objects::nonNull).toList();
        if (trackIds.isEmpty()) return;
        jdbc.sql("DELETE FROM catalog.track_artists WHERE track_id IN (:ids)").param("ids", trackIds).update();

        StringBuilder sql = new StringBuilder("INSERT INTO catalog.track_artists (track_id, artist_id, position) VALUES ");
        List<Object> params = new ArrayList<>();
        int n = 0;
        for (ProviderTrack t : sorted) {
            UUID trackId = trackIdByKey.get(key(t.ref()));
            if (trackId == null) continue;
            for (int position = 0; position < t.artists().size(); position++) {
                Artist artist = artistByKey.get(key(t.artists().get(position).ref()));
                if (artist == null) continue;
                sql.append(n++ == 0 ? "" : ", ").append("(?, ?, ?)");
                params.add(trackId);
                params.add(artist.id());
                params.add(position);
            }
        }
        if (n == 0) return;
        sql.append(" ON CONFLICT DO NOTHING");
        jdbc.sql(sql.toString()).params(params).update();
    }

    // ── Shared ─────────────────────────────────────────────────────────────────────────────

    private <S> void insertRefs(String table, String idColumn, List<S> sources, Function<S, UUID> idOf, Function<S, ProviderReference> refOf) {
        StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (" + idColumn + ", provider, provider_resource_id) VALUES ");
        List<Object> params = new ArrayList<>();
        int n = 0;
        for (S s : sources) {
            UUID id = idOf.apply(s);
            if (id == null) continue;
            ProviderReference ref = refOf.apply(s);
            sql.append(n++ == 0 ? "" : ", ").append("(?, ?, ?)");
            params.add(id);
            params.add(ref.provider().name());
            params.add(ref.providerResourceId());
        }
        if (n == 0) return;
        sql.append(" ON CONFLICT DO NOTHING");
        jdbc.sql(sql.toString()).params(params).update();
    }

    static String key(ProviderReference ref) {
        return ref.provider().name() + ":" + ref.providerResourceId();
    }
}
