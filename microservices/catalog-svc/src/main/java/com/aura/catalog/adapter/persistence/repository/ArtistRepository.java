package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.ProviderArtist;
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

/**
 * Hand-rolled JDBC access for {@code catalog.artists} (Project-Info.md §14: upsert-by-provider-ref
 * cache). Uses {@link JdbcClient} directly rather than a Spring Data JDBC {@code CrudRepository} —
 * the find-existing-row-then-update-or-insert-with-RETURNING flow doesn't map cleanly onto Spring
 * Data JDBC's aggregate-save model, and this is the kind of code that benefits from explicit SQL.
 */
@Repository
public class ArtistRepository {

    private final JdbcClient jdbc;
    private final ConflictRetryWriter conflictRetryWriter;

    public ArtistRepository(JdbcClient jdbc, ConflictRetryWriter conflictRetryWriter) {
        this.jdbc = jdbc;
        this.conflictRetryWriter = conflictRetryWriter;
    }

    public Optional<Artist> findById(UUID id) {
        return jdbc.sql("SELECT * FROM catalog.artists WHERE id = :id")
                .param("id", id)
                .query(ArtistRepository::mapRow)
                .optional()
                .map(this::hydrate);
    }

    public Optional<Artist> findByProviderRef(ProviderReference ref) {
        return findIdByProviderRef(ref).flatMap(this::findById);
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

    private Map<UUID, List<ProviderReference>> findRefsByIds(List<UUID> ids) {
        Map<UUID, List<ProviderReference>> result = new HashMap<>();
        jdbc.sql("SELECT artist_id, provider, provider_resource_id FROM catalog.artist_provider_refs WHERE artist_id IN (:ids)")
                .param("ids", ids)
                .query((rs, rowNum) -> Map.entry((UUID) rs.getObject("artist_id"),
                        new ProviderReference(Provider.valueOf(rs.getString("provider")), rs.getString("provider_resource_id"))))
                .list()
                .forEach(e -> result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return result;
    }

    /**
     * Insert-or-update by provider reference, then return the full domain {@link Artist}.
     * Runs its own transaction: callers (album/track upserts) that need this row committed
     * before referencing it via a foreign key call this directly rather than nesting transactions.
     *
     * {@code CatalogService} now upserts several tracks/albums/artists concurrently, so two
     * threads can both find no existing row for the same provider reference and both attempt an
     * insert — see {@link ConflictRetryWriter} for why the losing insert runs in its own
     * transaction and how the fallback to an update is safe.
     */
    @Transactional
    public Artist upsert(ProviderArtist source) {
        ProviderReference ref = source.ref();
        Optional<UUID> existingId = findIdByProviderRef(ref);

        ArtistRow row = existingId.isPresent() ? update(existingId.get(), source) : insertOrFallBackToUpdate(source, ref);
        List<ProviderReference> refs = findRefs(row.id());
        return toArtist(row, refs);
    }

    private ArtistRow insertOrFallBackToUpdate(ProviderArtist source, ProviderReference ref) {
        try {
            return conflictRetryWriter.runInNewTransaction(() -> insert(source, ref));
        } catch (DuplicateKeyException e) {
            UUID winnerId = findIdByProviderRef(ref).orElseThrow(() -> e);
            return update(winnerId, source);
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────

    private Optional<UUID> findIdByProviderRef(ProviderReference ref) {
        return jdbc.sql("""
                        SELECT artist_id FROM catalog.artist_provider_refs
                        WHERE provider = :provider AND provider_resource_id = :providerResourceId
                        """)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .query(UUID.class)
                .optional();
    }

    private ArtistRow update(UUID id, ProviderArtist source) {
        Artwork artwork = source.artwork();
        return jdbc.sql("""
                        UPDATE catalog.artists
                        SET name = :name, artwork_url = :artworkUrl, artwork_width = :artworkWidth,
                            artwork_height = :artworkHeight, popularity = :popularity,
                            provider_synced_at = now(), updated_at = now()
                        WHERE id = :id
                        RETURNING *
                        """)
                .param("id", id)
                .param("name", source.name())
                .param("artworkUrl", artwork == null ? null : artwork.url())
                .param("artworkWidth", artwork == null ? null : artwork.width())
                .param("artworkHeight", artwork == null ? null : artwork.height())
                .param("popularity", source.popularity())
                .query(ArtistRepository::mapRow)
                .single();
    }

    private ArtistRow insert(ProviderArtist source, ProviderReference ref) {
        UUID id = UUID.randomUUID();
        Artwork artwork = source.artwork();
        ArtistRow row = jdbc.sql("""
                        INSERT INTO catalog.artists
                            (id, name, artwork_url, artwork_width, artwork_height, popularity,
                             provider_synced_at, created_at, updated_at)
                        VALUES (:id, :name, :artworkUrl, :artworkWidth, :artworkHeight, :popularity, now(), now(), now())
                        RETURNING *
                        """)
                .param("id", id)
                .param("name", source.name())
                .param("artworkUrl", artwork == null ? null : artwork.url())
                .param("artworkWidth", artwork == null ? null : artwork.width())
                .param("artworkHeight", artwork == null ? null : artwork.height())
                .param("popularity", source.popularity())
                .query(ArtistRepository::mapRow)
                .single();

        jdbc.sql("INSERT INTO catalog.artist_provider_refs (artist_id, provider, provider_resource_id) VALUES (:id, :provider, :providerResourceId)")
                .param("id", id)
                .param("provider", ref.provider().name())
                .param("providerResourceId", ref.providerResourceId())
                .update();

        return row;
    }

    private List<ProviderReference> findRefs(UUID id) {
        return jdbc.sql("SELECT provider, provider_resource_id FROM catalog.artist_provider_refs WHERE artist_id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new ProviderReference(
                        Provider.valueOf(rs.getString("provider")),
                        rs.getString("provider_resource_id")))
                .list();
    }

    private Artist hydrate(ArtistRow row) {
        return toArtist(row, findRefs(row.id()));
    }

    private static Artist toArtist(ArtistRow row, List<ProviderReference> refs) {
        return new Artist(row.id(), row.name(), row.artwork(), row.popularity(), refs, row.providerSyncedAt(), row.createdAt(), row.updatedAt());
    }

    private static ArtistRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String artworkUrl = rs.getString("artwork_url");
        Artwork artwork = artworkUrl == null ? null
                : new Artwork(artworkUrl, rs.getInt("artwork_width"), rs.getInt("artwork_height"));
        return new ArtistRow(
                (UUID) rs.getObject("id"),
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

    private record ArtistRow(UUID id, String name, Artwork artwork, double popularity,
                              Instant providerSyncedAt, Instant createdAt, Instant updatedAt) {
    }
}
