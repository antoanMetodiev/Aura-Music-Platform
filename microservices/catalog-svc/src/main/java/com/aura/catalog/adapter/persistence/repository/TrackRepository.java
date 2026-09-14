package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.Track;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read side of {@code catalog.tracks} — see {@link ArtistRepository}. Writes go through {@link CatalogBatchWriter}. */
@Repository
public class TrackRepository {

    private final JdbcClient jdbc;
    private final AlbumRepository albums;
    private final ArtistRepository artists;

    public TrackRepository(JdbcClient jdbc, AlbumRepository albums, ArtistRepository artists) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.artists = artists;
    }

    public Optional<Track> findById(UUID id) {
        return findByIds(List.of(id)).stream().findFirst();
    }

    public Optional<Track> findByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT track_id FROM catalog.track_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional()
                .flatMap(this::findById);
    }

    public List<Track> findByIsrc(String isrc) {
        List<UUID> ids = jdbc.sql("SELECT id FROM catalog.tracks WHERE isrc = :isrc")
                .param("isrc", isrc)
                .query(UUID.class)
                .list();
        return findByIds(ids);
    }

    public List<Track> findByAlbumId(UUID albumId) {
        List<UUID> ids = jdbc.sql("""
                        SELECT id FROM catalog.tracks WHERE album_id = :albumId
                        ORDER BY volume_number NULLS LAST, track_number NULLS LAST, title
                        """)
                .param("albumId", albumId)
                .query(UUID.class)
                .list();
        return findByIds(ids);
    }

    /**
     * Input order preserved; ids with no row are skipped. A fixed handful of round trips for the
     * whole list (tracks, their albums + album artists, track artists, refs) instead of ~4 per track.
     */
    public List<Track> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(ids));
        List<TrackRow> rows = jdbc.sql("SELECT * FROM catalog.tracks WHERE id IN (:ids)")
                .param("ids", distinct)
                .query(TrackRepository::mapRow)
                .list();
        if (rows.isEmpty()) return List.of();

        Map<UUID, Album> albumsById = albums.findByIdsMap(
                rows.stream().map(TrackRow::albumId).filter(Objects::nonNull).toList());

        Map<UUID, List<UUID>> artistIdsByTrack = new HashMap<>();
        jdbc.sql("SELECT track_id, artist_id FROM catalog.track_artists WHERE track_id IN (:ids) ORDER BY track_id, position")
                .param("ids", distinct)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("track_id"), (UUID) rs.getObject("artist_id")))
                .list()
                .forEach(e -> artistIdsByTrack.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        Map<UUID, Artist> artistsById = artists.findByIdsMap(
                artistIdsByTrack.values().stream().flatMap(List::stream).toList());

        Map<UUID, List<ProviderReference>> refs = findRefsByIds(distinct);

        Map<UUID, Track> byId = new HashMap<>();
        for (TrackRow row : rows) {
            Album album = row.albumId() == null ? null : albumsById.get(row.albumId());
            List<Artist> trackArtists = artistIdsByTrack.getOrDefault(row.id(), List.of()).stream()
                    .map(artistsById::get).filter(Objects::nonNull).toList();
            byId.put(row.id(), toTrack(row, album, trackArtists, refs.getOrDefault(row.id(), List.of())));
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    Map<UUID, List<ProviderReference>> findRefsByIds(List<UUID> ids) {
        Map<UUID, List<ProviderReference>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.sql("SELECT track_id, provider, provider_resource_id FROM catalog.track_provider_refs WHERE track_id IN (:ids)")
                .param("ids", ids)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("track_id"),
                        new ProviderReference(Provider.valueOf(rs.getString("provider")), rs.getString("provider_resource_id"))))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    static Track toTrack(TrackRow row, Album album, List<Artist> trackArtists, List<ProviderReference> refs) {
        return new Track(row.id(), row.title(), row.version(), row.durationMs(), row.isrc(), row.explicit(),
                row.popularity(), album, trackArtists, row.volumeNumber(), row.trackNumber(), refs,
                row.providerSyncedAt(), row.createdAt(), row.updatedAt());
    }

    static TrackRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object albumIdObj = rs.getObject("album_id");
        return new TrackRow(
                (UUID) rs.getObject("id"),
                rs.getString("primary_ref"),
                rs.getString("title"),
                rs.getString("version"),
                rs.getLong("duration_ms"),
                rs.getString("isrc"),
                rs.getBoolean("explicit"),
                rs.getDouble("popularity"),
                albumIdObj == null ? null : (UUID) albumIdObj,
                rs.getObject("volume_number", Integer.class),
                rs.getObject("track_number", Integer.class),
                ArtistRepository.toInstant(rs, "provider_synced_at"),
                ArtistRepository.toInstant(rs, "created_at"),
                ArtistRepository.toInstant(rs, "updated_at")
        );
    }

    record TrackRow(UUID id, String primaryRef, String title, String version, long durationMs, String isrc, boolean explicit,
                    double popularity, UUID albumId, Integer volumeNumber, Integer trackNumber,
                    Instant providerSyncedAt, Instant createdAt, Instant updatedAt) {
    }
}
