package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
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

/**
 * Read side of {@code catalog.artists}, hand-rolled on {@link JdbcClient} rather than a Spring Data
 * JDBC {@code CrudRepository} — this is the kind of code that benefits from explicit SQL. All writes
 * go through {@link CatalogBatchWriter}.
 */
@Repository
public class ArtistRepository {

    private final JdbcClient jdbc;

    public ArtistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Artist> findById(UUID id) {
        return jdbc.sql("SELECT * FROM catalog.artists WHERE id = :id")
                .param("id", id)
                .query(ArtistRepository::mapRow)
                .optional()
                .map(row -> toArtist(row, findRefs(row.id())));
    }

    public Optional<Artist> findByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT artist_id FROM catalog.artist_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional()
                .flatMap(this::findById);
    }

    /** Input order preserved; ids with no row are skipped. Two round trips regardless of list size. */
    public List<Artist> findByIds(Collection<UUID> ids) {
        Map<UUID, Artist> byId = findByIdsMap(ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    Map<UUID, Artist> findByIdsMap(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(ids));
        List<ArtistRow> rows = jdbc.sql("SELECT * FROM catalog.artists WHERE id IN (:ids)")
                .param("ids", distinct)
                .query(ArtistRepository::mapRow)
                .list();
        Map<UUID, List<ProviderReference>> refs = findRefsByIds(distinct);
        Map<UUID, Artist> result = new HashMap<>();
        for (ArtistRow row : rows) {
            result.put(row.id(), toArtist(row, refs.getOrDefault(row.id(), List.of())));
        }
        return result;
    }

    Map<UUID, List<ProviderReference>> findRefsByIds(List<UUID> ids) {
        Map<UUID, List<ProviderReference>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        jdbc.sql("SELECT artist_id, provider, provider_resource_id FROM catalog.artist_provider_refs WHERE artist_id IN (:ids)")
                .param("ids", ids)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("artist_id"),
                        new ProviderReference(Provider.valueOf(rs.getString("provider")), rs.getString("provider_resource_id"))))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    private List<ProviderReference> findRefs(UUID id) {
        return findRefsByIds(List.of(id)).getOrDefault(id, List.of());
    }

    static Artist toArtist(ArtistRow row, List<ProviderReference> refs) {
        return new Artist(row.id(), row.name(), row.artwork(), row.popularity(), refs, row.providerSyncedAt(), row.createdAt(), row.updatedAt());
    }

    static ArtistRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String artworkUrl = rs.getString("artwork_url");
        Artwork artwork = artworkUrl == null ? null
                : new Artwork(artworkUrl, rs.getInt("artwork_width"), rs.getInt("artwork_height"));
        return new ArtistRow(
                (UUID) rs.getObject("id"),
                rs.getString("primary_ref"),
                rs.getString("name"),
                artwork,
                rs.getDouble("popularity"),
                toInstant(rs, "provider_synced_at"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    record ArtistRow(UUID id, String primaryRef, String name, Artwork artwork, double popularity,
                     Instant providerSyncedAt, Instant createdAt, Instant updatedAt) {
    }
}
