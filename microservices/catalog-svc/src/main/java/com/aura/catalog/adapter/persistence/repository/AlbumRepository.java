package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.ProviderAlbum;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hand-rolled JDBC access for {@code catalog.albums} — see {@link ArtistRepository} for the pattern. */
@Repository
public class AlbumRepository {

    private final JdbcClient jdbc;
    private final ArtistRepository artists;
    private final ConflictRetryWriter conflictRetryWriter;

    public AlbumRepository(JdbcClient jdbc, ArtistRepository artists, ConflictRetryWriter conflictRetryWriter) {
        this.jdbc = jdbc;
        this.artists = artists;
        this.conflictRetryWriter = conflictRetryWriter;
    }

    public Optional<Album> findById(UUID id) {
        return jdbc.sql("SELECT * FROM catalog.albums WHERE id = :id")
                .param("id", id)
                .query(AlbumRepository::mapRow)
                .optional()
                .map(this::hydrate);
    }

    public Optional<Album> findByProviderRef(ProviderReference ref) {
        return findIdByProviderRef(ref).flatMap(this::findById);
    }

    /**
     * Cascades into {@link ArtistRepository} for the album artist before writing this row.
     * See {@link ArtistRepository#upsert} for why the insert path falls back to an update on a lost
     * race — the same concurrent-upsert scenario applies here whenever two tracks from the same
     * album are hydrated in parallel.
     */
    @Transactional
    public Album upsert(ProviderAlbum source) {
        Artist artist = source.artist() == null ? null : artists.upsert(source.artist());
        ProviderReference ref = source.ref();
        Optional<UUID> existingId = findIdByProviderRef(ref);

        AlbumRow row = existingId.isPresent() ? update(existingId.get(), source, artist) : insertOrFallBackToUpdate(source, ref, artist);
        List<ProviderReference> refs = findRefs(row.id());
        return toAlbum(row, artist, refs);
    }

    private AlbumRow insertOrFallBackToUpdate(ProviderAlbum source, ProviderReference ref, Artist artist) {
        try {
            return conflictRetryWriter.runInNewTransaction(() -> insert(source, ref, artist));
        } catch (DuplicateKeyException e) {
            UUID winnerId = findIdByProviderRef(ref).orElseThrow(() -> e);
            return update(winnerId, source, artist);
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────

    private Optional<UUID> findIdByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT album_id FROM catalog.album_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional();
    }

    private AlbumRow update(UUID id, ProviderAlbum source, Artist artist) {
        Artwork artwork = source.artwork();
        return jdbc.sql("""
                        UPDATE catalog.albums
                        SET title = :title, album_type = :albumType, release_date = :releaseDate,
                            artist_id = :artistId, artwork_url = :artworkUrl, artwork_width = :artworkWidth,
                            artwork_height = :artworkHeight, explicit = :explicit, number_of_tracks = :numberOfTracks,
                            popularity = :popularity, provider_synced_at = now(), updated_at = now()
                        WHERE id = :id
                        RETURNING *
                        """)
                .param("id", id)
                .param("title", source.title())
                .param("albumType", source.type().name())
                .param("releaseDate", source.releaseDate())
                .param("artistId", artist == null ? null : artist.id())
                .param("artworkUrl", artwork == null ? null : artwork.url())
                .param("artworkWidth", artwork == null ? null : artwork.width())
                .param("artworkHeight", artwork == null ? null : artwork.height())
                .param("explicit", source.explicit())
                .param("numberOfTracks", source.numberOfTracks())
                .param("popularity", source.popularity())
                .query(AlbumRepository::mapRow)
                .single();
    }

    private AlbumRow insert(ProviderAlbum source, ProviderReference ref, Artist artist) {
        UUID id = UUID.randomUUID();
        Artwork artwork = source.artwork();
        AlbumRow row = jdbc.sql("""
                        INSERT INTO catalog.albums
                            (id, title, album_type, release_date, artist_id, artwork_url, artwork_width,
                             artwork_height, explicit, number_of_tracks, popularity, provider_synced_at,
                             created_at, updated_at)
                        VALUES (:id, :title, :albumType, :releaseDate, :artistId, :artworkUrl, :artworkWidth,
                                :artworkHeight, :explicit, :numberOfTracks, :popularity, now(), now(), now())
                        RETURNING *
                        """)
                .param("id", id)
                .param("title", source.title())
                .param("albumType", source.type().name())
                .param("releaseDate", source.releaseDate())
                .param("artistId", artist == null ? null : artist.id())
                .param("artworkUrl", artwork == null ? null : artwork.url())
                .param("artworkWidth", artwork == null ? null : artwork.width())
                .param("artworkHeight", artwork == null ? null : artwork.height())
                .param("explicit", source.explicit())
                .param("numberOfTracks", source.numberOfTracks())
                .param("popularity", source.popularity())
                .query(AlbumRepository::mapRow)
                .single();

        jdbc.sql("INSERT INTO catalog.album_provider_refs (album_id, provider, provider_resource_id) VALUES (:id, :provider, :providerResourceId)")
                .param("id", id)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .update();

        return row;
    }

    private List<ProviderReference> findRefs(UUID id) {
        return jdbc.sql("SELECT provider, provider_resource_id FROM catalog.album_provider_refs WHERE album_id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new ProviderReference(
                        Provider.valueOf(rs.getString("provider")),
                        rs.getString("provider_resource_id")))
                .list();
    }

    private Album hydrate(AlbumRow row) {
        Artist artist = row.artistId() == null ? null : artists.findById(row.artistId()).orElse(null);
        return toAlbum(row, artist, findRefs(row.id()));
    }

    private static Album toAlbum(AlbumRow row, Artist artist, List<ProviderReference> refs) {
        return new Album(row.id(), row.title(), row.albumType(), row.releaseDate(), artist, row.artwork(),
                row.explicit(), row.numberOfTracks(), row.popularity(), refs, row.providerSyncedAt(),
                row.createdAt(), row.updatedAt());
    }

    private static AlbumRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String artworkUrl = rs.getString("artwork_url");
        Artwork artwork = artworkUrl == null ? null
                : new Artwork(artworkUrl, rs.getInt("artwork_width"), rs.getInt("artwork_height"));
        java.sql.Date releaseDate = rs.getDate("release_date");
        Object artistIdObj = rs.getObject("artist_id");
        return new AlbumRow(
                (UUID) rs.getObject("id"),
                rs.getString("title"),
                AlbumType.valueOf(rs.getString("album_type")),
                releaseDate == null ? null : releaseDate.toLocalDate(),
                artistIdObj == null ? null : (UUID) artistIdObj,
                artwork,
                rs.getBoolean("explicit"),
                rs.getInt("number_of_tracks"),
                rs.getDouble("popularity"),
                ArtistRepository.toInstant(rs, "provider_synced_at"),
                ArtistRepository.toInstant(rs, "created_at"),
                ArtistRepository.toInstant(rs, "updated_at")
        );
    }

    private record AlbumRow(UUID id, String title, AlbumType albumType, LocalDate releaseDate, UUID artistId,
                             Artwork artwork, boolean explicit, int numberOfTracks, double popularity,
                             Instant providerSyncedAt, Instant createdAt, Instant updatedAt) {
    }
}
