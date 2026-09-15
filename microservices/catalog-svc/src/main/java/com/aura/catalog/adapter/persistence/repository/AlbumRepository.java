package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read side of {@code catalog.albums} — see {@link ArtistRepository}. Writes go through {@link CatalogBatchWriter}. */
@Repository
public class AlbumRepository {

    private final JdbcClient jdbc;
    private final ArtistRepository artists;

    public AlbumRepository(JdbcClient jdbc, ArtistRepository artists) {
        this.jdbc = jdbc;
        this.artists = artists;
    }

    public Optional<Album> findById(UUID id) {
        return findByIds(List.of(id)).stream().findFirst();
    }

    public Optional<Album> findByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT album_id FROM catalog.album_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional()
                .flatMap(this::findById);
    }

    /** Input order preserved; ids with no row are skipped. Four round trips regardless of list size. */
    public List<Album> findByIds(Collection<UUID> ids) {
        Map<UUID, Album> byId = findByIdsMap(ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    Map<UUID, Album> findByIdsMap(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(ids));
        List<AlbumRow> rows = jdbc.sql("SELECT * FROM catalog.albums WHERE id IN (:ids)")
                .param("ids", distinct)
                .query(AlbumRepository::mapRow)
                .list();
        Map<UUID, Artist> artistsById = artists.findByIdsMap(
                rows.stream().map(AlbumRow::artistId).filter(Objects::nonNull).toList());
        Map<UUID, List<ProviderReference>> refs = findRefsByIds(distinct);
        Map<UUID, Album> result = new HashMap<>();
        for (AlbumRow row : rows) {
            Artist artist = row.artistId() == null ? null : artistsById.get(row.artistId());
            result.put(row.id(), toAlbum(row, artist, refs.getOrDefault(row.id(), List.of())));
        }
        return result;
    }

    Map<UUID, List<ProviderReference>> findRefsByIds(List<UUID> ids) {
        Map<UUID, List<ProviderReference>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.sql("SELECT album_id, provider, provider_resource_id FROM catalog.album_provider_refs WHERE album_id IN (:ids)")
                .param("ids", ids)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("album_id"),
                        new ProviderReference(Provider.valueOf(rs.getString("provider")), rs.getString("provider_resource_id"))))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    /** Albums credited to the artist, newest first. */
    /** Albums owned by any of these artist rows (a canonical and its aliases), newest first. */
    public List<Album> findByArtistIds(Collection<UUID> artistIds) {
        String[] arr = artistIds.stream().map(UUID::toString).toArray(String[]::new);
        List<UUID> ids = jdbc.sql("""
                        SELECT id FROM catalog.albums WHERE artist_id = ANY(CAST(:artistIds AS uuid[]))
                        ORDER BY release_date DESC NULLS LAST, title
                        """)
                .param("artistIds", arr)
                .query(UUID.class)
                .list();
        return findByIds(ids);
    }

    public void markTracksSynced(UUID albumId) {
        jdbc.sql("UPDATE catalog.albums SET tracks_synced_at = now() WHERE id = :id")
                .param("id", albumId)
                .update();
    }

    static Album toAlbum(AlbumRow row, Artist artist, List<ProviderReference> refs) {
        return new Album(row.id(), row.title(), row.albumType(), row.releaseDate(), artist, row.artwork(),
                row.explicit(), row.numberOfTracks(), row.popularity(), refs, row.providerSyncedAt(),
                row.tracksSyncedAt(), row.createdAt(), row.updatedAt());
    }

    static AlbumRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String artworkUrl = rs.getString("artwork_url");
        Artwork artwork = artworkUrl == null ? null
                : new Artwork(artworkUrl, rs.getInt("artwork_width"), rs.getInt("artwork_height"));
        java.sql.Date releaseDate = rs.getDate("release_date");
        Object artistIdObj = rs.getObject("artist_id");
        return new AlbumRow(
                (UUID) rs.getObject("id"),
                rs.getString("primary_ref"),
                rs.getString("title"),
                AlbumType.valueOf(rs.getString("album_type")),
                releaseDate == null ? null : releaseDate.toLocalDate(),
                artistIdObj == null ? null : (UUID) artistIdObj,
                artwork,
                rs.getBoolean("explicit"),
                rs.getInt("number_of_tracks"),
                rs.getDouble("popularity"),
                ArtistRepository.toInstant(rs, "provider_synced_at"),
                ArtistRepository.toInstant(rs, "tracks_synced_at"),
                ArtistRepository.toInstant(rs, "created_at"),
                ArtistRepository.toInstant(rs, "updated_at")
        );
    }

    record AlbumRow(UUID id, String primaryRef, String title, AlbumType albumType, LocalDate releaseDate, UUID artistId,
                    Artwork artwork, boolean explicit, int numberOfTracks, double popularity,
                    Instant providerSyncedAt, Instant tracksSyncedAt, Instant createdAt, Instant updatedAt) {
    }
}
