package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.model.Track;
import com.aura.catalog.domain.port.ProviderTrack;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

/** Hand-rolled JDBC access for {@code catalog.tracks} — see {@link ArtistRepository} for the pattern. */
@Repository
public class TrackRepository {

    private final JdbcClient jdbc;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final ConflictRetryWriter conflictRetryWriter;

    public TrackRepository(JdbcClient jdbc, AlbumRepository albums, ArtistRepository artists, ConflictRetryWriter conflictRetryWriter) {
        this.jdbc = jdbc;
        this.albums = albums;
        this.artists = artists;
        this.conflictRetryWriter = conflictRetryWriter;
    }

    public Optional<Track> findById(UUID id) {
        return jdbc.sql("SELECT * FROM catalog.tracks WHERE id = :id")
                .param("id", id)
                .query(TrackRepository::mapRow)
                .optional()
                .map(this::hydrate);
    }

    public Optional<Track> findByProviderRef(ProviderReference ref) {
        return findIdByProviderRef(ref).flatMap(this::findById);
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

        Map<UUID, List<ProviderReference>> refs = new HashMap<>();
        jdbc.sql("SELECT track_id, provider, provider_resource_id FROM catalog.track_provider_refs WHERE track_id IN (:ids)")
                .param("ids", distinct)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("track_id"),
                        new ProviderReference(Provider.valueOf(rs.getString("provider")), rs.getString("provider_resource_id"))))
                .list()
                .forEach(e -> refs.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        Map<UUID, Track> byId = new HashMap<>();
        for (TrackRow row : rows) {
            Album album = row.albumId() == null ? null : albumsById.get(row.albumId());
            List<Artist> trackArtists = artistIdsByTrack.getOrDefault(row.id(), List.of()).stream()
                    .map(artistsById::get).filter(Objects::nonNull).toList();
            byId.put(row.id(), toTrack(row, album, trackArtists, refs.getOrDefault(row.id(), List.of())));
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public List<Track> findByIsrc(String isrc) {
        return jdbc.sql("SELECT * FROM catalog.tracks WHERE isrc = :isrc")
                .param("isrc", isrc)
                .query(TrackRepository::mapRow)
                .list()
                .stream().map(this::hydrate).toList();
    }

    /**
     * Cascades into {@link AlbumRepository} and {@link ArtistRepository} before writing this row.
     * See {@link ArtistRepository#upsert} for why the insert path falls back to an update on a lost
     * race (e.g. this same track discovered concurrently via two different requests).
     */
    @Transactional
    public Track upsert(ProviderTrack source) {
        Album album = source.album() == null ? null : albums.upsert(source.album());
        List<Artist> trackArtists = source.artists().stream().map(artists::upsert).toList();

        ProviderReference ref = source.ref();
        Optional<UUID> existingId = findIdByProviderRef(ref);

        TrackRow row = existingId.isPresent()
                ? update(existingId.get(), source, album)
                : insertOrFallBackToUpdate(source, ref, album);

        replaceTrackArtists(row.id(), trackArtists);
        List<ProviderReference> refs = findRefs(row.id());
        return toTrack(row, album, trackArtists, refs);
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────

    private Optional<UUID> findIdByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT track_id FROM catalog.track_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional();
    }

    private TrackRow update(UUID id, ProviderTrack source, Album album) {
        return jdbc.sql("""
                        UPDATE catalog.tracks
                        SET title = :title, version = :version, duration_ms = :durationMs, isrc = :isrc,
                            explicit = :explicit, popularity = :popularity, album_id = :albumId,
                            provider_synced_at = now(), updated_at = now()
                        WHERE id = :id
                        RETURNING *
                        """)
                .param("id", id)
                .param("title", source.title())
                .param("version", source.version())
                .param("durationMs", source.durationMs())
                .param("isrc", source.isrc())
                .param("explicit", source.explicit())
                .param("popularity", source.popularity())
                .param("albumId", album == null ? null : album.id())
                .query(TrackRepository::mapRow)
                .single();
    }

    private TrackRow insertOrFallBackToUpdate(ProviderTrack source, ProviderReference ref, Album album) {
        try {
            return conflictRetryWriter.runInNewTransaction(() -> insert(source, ref, album));
        } catch (DuplicateKeyException e) {
            UUID winnerId = findIdByProviderRef(ref).orElseThrow(() -> e);
            return update(winnerId, source, album);
        }
    }

    private TrackRow insert(ProviderTrack source, ProviderReference ref, Album album) {
        UUID id = UUID.randomUUID();
        TrackRow row = jdbc.sql("""
                        INSERT INTO catalog.tracks
                            (id, title, version, duration_ms, isrc, explicit, popularity, album_id,
                             provider_synced_at, created_at, updated_at)
                        VALUES (:id, :title, :version, :durationMs, :isrc, :explicit, :popularity, :albumId,
                                now(), now(), now())
                        RETURNING *
                        """)
                .param("id", id)
                .param("title", source.title())
                .param("version", source.version())
                .param("durationMs", source.durationMs())
                .param("isrc", source.isrc())
                .param("explicit", source.explicit())
                .param("popularity", source.popularity())
                .param("albumId", album == null ? null : album.id())
                .query(TrackRepository::mapRow)
                .single();

        jdbc.sql("INSERT INTO catalog.track_provider_refs (track_id, provider, provider_resource_id) VALUES (:id, :provider, :providerResourceId)")
                .param("id", id)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .update();

        return row;
    }

    private void replaceTrackArtists(UUID trackId, List<Artist> trackArtists) {
        jdbc.sql("DELETE FROM catalog.track_artists WHERE track_id = :trackId").param("trackId", trackId).update();
        for (int position = 0; position < trackArtists.size(); position++) {
            jdbc.sql("INSERT INTO catalog.track_artists (track_id, artist_id, position) VALUES (:trackId, :artistId, :position)")
                    .param("trackId", trackId)
                    .param("artistId", trackArtists.get(position).id())
                    .param("position", position)
                    .update();
        }
    }

    private List<ProviderReference> findRefs(UUID id) {
        return jdbc.sql("SELECT provider, provider_resource_id FROM catalog.track_provider_refs WHERE track_id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new ProviderReference(
                        Provider.valueOf(rs.getString("provider")),
                        rs.getString("provider_resource_id")))
                .list();
    }

    private List<Artist> findTrackArtists(UUID trackId) {
        List<UUID> artistIds = jdbc.sql("SELECT artist_id FROM catalog.track_artists WHERE track_id = :trackId ORDER BY position")
                .param("trackId", trackId)
                .query(UUID.class)
                .list();
        List<Artist> result = new ArrayList<>(artistIds.size());
        for (UUID artistId : artistIds) {
            artists.findById(artistId).ifPresent(result::add);
        }
        return result;
    }

    private Track hydrate(TrackRow row) {
        Album album = row.albumId() == null ? null : albums.findById(row.albumId()).orElse(null);
        return toTrack(row, album, findTrackArtists(row.id()), findRefs(row.id()));
    }

    private static Track toTrack(TrackRow row, Album album, List<Artist> trackArtists, List<ProviderReference> refs) {
        return new Track(row.id(), row.title(), row.version(), row.durationMs(), row.isrc(), row.explicit(),
                row.popularity(), album, trackArtists, refs, row.providerSyncedAt(), row.createdAt(), row.updatedAt());
    }

    private static TrackRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object albumIdObj = rs.getObject("album_id");
        return new TrackRow(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                rs.getString("version"),
                rs.getLong("duration_ms"),
                rs.getString("isrc"),
                rs.getBoolean("explicit"),
                rs.getDouble("popularity"),
                albumIdObj == null ? null : (UUID) albumIdObj,
                ArtistRepository.toInstant(rs, "provider_synced_at"),
                ArtistRepository.toInstant(rs, "created_at"),
                ArtistRepository.toInstant(rs, "updated_at")
        );
    }

    private record TrackRow(UUID id, String title, String version, long durationMs, String isrc, boolean explicit,
                             double popularity, UUID albumId, Instant providerSyncedAt, Instant createdAt,
                             Instant updatedAt) {
    }
}
