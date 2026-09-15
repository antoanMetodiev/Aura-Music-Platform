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
import java.util.Locale;
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

    /** One artist per distinct name (a canonical over an alias, then the most popular), any order. */
    public List<Artist> findByExactNames(Collection<String> names) {
        if (names.isEmpty()) return List.of();
        String[] lowered = names.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).distinct().toArray(String[]::new);
        List<UUID> ids = jdbc.sql("""
                        SELECT DISTINCT ON (lower(name)) id FROM catalog.artists
                        WHERE lower(name) = ANY(:names)
                        ORDER BY lower(name), (canonical_artist_id IS NULL) DESC, popularity DESC
                        """)
                .param("names", lowered)
                .query(UUID.class)
                .list();
        return findByIds(ids);
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
        return new Artist(row.id(), row.name(), row.artwork(), row.popularity(), row.canonicalArtistId(), refs,
                row.providerSyncedAt(), row.createdAt(), row.updatedAt());
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
                (UUID) rs.getObject("canonical_artist_id"),
                toInstant(rs, "provider_synced_at"),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    static Instant toInstant(ResultSet rs, String column) throws SQLException {
        var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    record ArtistRow(UUID id, String primaryRef, String name, Artwork artwork, double popularity, UUID canonicalArtistId,
                     Instant providerSyncedAt, Instant createdAt, Instant updatedAt) {
    }

    // ── Duplicate artists (V14) ────────────────────────────────────────────────────────────

    /** Every row with this name (case-insensitive), canonical or alias. */
    public List<Artist> findByNormalizedName(String name) {
        List<UUID> ids = jdbc.sql("SELECT id FROM catalog.artists WHERE lower(name) = :name ORDER BY popularity DESC")
                .param("name", name.strip().toLowerCase(Locale.ROOT))
                .query(UUID.class)
                .list();
        return findByIds(ids);
    }

    /** Names carried by more than one row — the backfill's worklist. */
    public List<String> findDuplicatedNames() {
        return jdbc.sql("SELECT lower(name) FROM catalog.artists GROUP BY lower(name) HAVING count(*) > 1 ORDER BY 1")
                .query(String.class)
                .list();
    }

    /** The canonical artist and every alias pointing at it (the canonical id first). */
    public List<UUID> findGroupIds(UUID canonicalId) {
        List<UUID> ids = new ArrayList<>();
        ids.add(canonicalId);
        ids.addAll(jdbc.sql("SELECT id FROM catalog.artists WHERE canonical_artist_id = :id")
                .param("id", canonicalId)
                .query(UUID.class)
                .list());
        return ids;
    }

    /**
     * Whether two same-named rows are demonstrably the same artist: they are credited on a track with
     * the same ISRC (the same recording), or on tracks of the same album, or one owns an album the
     * other is credited on. Any of those is evidence a provider split one artist into two profiles.
     */
    public boolean shareRecordingOrRelease(UUID a, UUID b) {
        Boolean shared = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM catalog.track_artists ta1
                            JOIN catalog.tracks t1 ON t1.id = ta1.track_id
                            JOIN catalog.tracks t2 ON t2.isrc = t1.isrc
                            JOIN catalog.track_artists ta2 ON ta2.track_id = t2.id
                            WHERE ta1.artist_id = :a AND ta2.artist_id = :b AND t1.isrc IS NOT NULL
                        ) OR EXISTS (
                            SELECT 1 FROM catalog.track_artists ta1
                            JOIN catalog.tracks t1 ON t1.id = ta1.track_id
                            JOIN catalog.tracks t2 ON t2.album_id = t1.album_id
                            JOIN catalog.track_artists ta2 ON ta2.track_id = t2.id
                            WHERE ta1.artist_id = :a AND ta2.artist_id = :b AND t1.album_id IS NOT NULL
                        ) OR EXISTS (
                            SELECT 1 FROM catalog.albums al
                            JOIN catalog.tracks t ON t.album_id = al.id
                            JOIN catalog.track_artists ta ON ta.track_id = t.id
                            WHERE (al.artist_id = :a AND ta.artist_id = :b) OR (al.artist_id = :b AND ta.artist_id = :a)
                        )
                        """)
                .param("a", a)
                .param("b", b)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(shared);
    }

    /** Tracks credited to each of these rows alone (not their groups) — the tie-breaker for picking a canonical. */
    public Map<UUID, Integer> countOwnTracks(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        String[] arr = ids.stream().map(UUID::toString).toArray(String[]::new);
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.sql("SELECT artist_id, count(*) AS n FROM catalog.track_artists WHERE artist_id = ANY(CAST(:ids AS uuid[])) GROUP BY artist_id")
                .param("ids", arr)
                .query((rs, n) -> Map.entry((UUID) rs.getObject("artist_id"), rs.getInt("n")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    /** Points every alias (and anything that used to point at them) at the canonical, which is cleared itself. */
    public void setCanonical(Collection<UUID> aliases, UUID canonical) {
        if (!aliases.isEmpty()) {
            String[] arr = aliases.stream().map(UUID::toString).toArray(String[]::new);
            jdbc.sql("""
                            UPDATE catalog.artists SET canonical_artist_id = :canonical, updated_at = now()
                            WHERE id = ANY(CAST(:ids AS uuid[])) OR canonical_artist_id = ANY(CAST(:ids AS uuid[]))
                            """)
                    .param("canonical", canonical)
                    .param("ids", arr)
                    .update();
        }
        jdbc.sql("UPDATE catalog.artists SET canonical_artist_id = NULL WHERE id = :id AND canonical_artist_id IS NOT NULL")
                .param("id", canonical)
                .update();
    }

    /** Tracks credited to anyone in each canonical's group — the "how much do we know about them" signal search ranks by. */
    public Map<UUID, Integer> countGroupTracks(Collection<UUID> canonicalIds) {
        if (canonicalIds.isEmpty()) return Map.of();
        String[] arr = canonicalIds.stream().map(UUID::toString).toArray(String[]::new);
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.sql("""
                        SELECT COALESCE(a.canonical_artist_id, a.id) AS cid, count(*) AS n
                        FROM catalog.track_artists ta
                        JOIN catalog.artists a ON a.id = ta.artist_id
                        WHERE COALESCE(a.canonical_artist_id, a.id) = ANY(CAST(:ids AS uuid[]))
                        GROUP BY 1
                        """)
                .param("ids", arr)
                .query((rs, n) -> Map.entry((UUID) rs.getObject("cid"), rs.getInt("n")))
                .list()
                .forEach(e -> counts.put(e.getKey(), e.getValue()));
        return counts;
    }

    /**
     * A profile that exists only as a "feat." credit: at most {@code maxTracks} tracks, primary
     * artist on none of them, owner of no album. That is what a label's upload of a guest appearance
     * under a fresh profile looks like.
     */
    public boolean isFeatureOnlyProfile(UUID id, int maxTracks) {
        Boolean featureOnly = jdbc.sql("""
                        SELECT (SELECT count(*) FROM catalog.track_artists WHERE artist_id = :id) BETWEEN 1 AND :max
                           AND NOT EXISTS (SELECT 1 FROM catalog.track_artists WHERE artist_id = :id AND position = 0)
                           AND NOT EXISTS (SELECT 1 FROM catalog.albums WHERE artist_id = :id)
                        """)
                .param("id", id)
                .param("max", maxTracks)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(featureOnly);
    }

    /** Whether the two rows have tracks whose ISRCs were issued in the same country (the first two characters). */
    public boolean shareIsrcCountry(UUID a, UUID b) {
        Boolean shared = jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM catalog.track_artists ta1 JOIN catalog.tracks t1 ON t1.id = ta1.track_id
                            JOIN catalog.track_artists ta2 JOIN catalog.tracks t2 ON t2.id = ta2.track_id
                              ON substr(t2.isrc, 1, 2) = substr(t1.isrc, 1, 2)
                            WHERE ta1.artist_id = :a AND ta2.artist_id = :b AND t1.isrc IS NOT NULL AND t2.isrc IS NOT NULL
                        )
                        """)
                .param("a", a)
                .param("b", b)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(shared);
    }
}
