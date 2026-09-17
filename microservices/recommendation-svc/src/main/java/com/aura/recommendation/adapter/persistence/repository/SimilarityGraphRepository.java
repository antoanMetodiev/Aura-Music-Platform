package com.aura.recommendation.adapter.persistence.repository;

import com.aura.recommendation.domain.model.ArtistTag;
import com.aura.recommendation.domain.model.Provider;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@link SimilarityGraphStore} over {@code recommendation.artist_similarity} and {@code artist_tags}.
 *
 * <p>Writes go in as one statement per artist rather than a row at a time: the arrays are bound as
 * {@code text[]} and cast inside the query ({@code pgjdbc} encodes {@code String[]} natively but not
 * {@code UUID[]}/{@code Double[]}), then expanded with {@code unnest}. Sixty edges cost one round trip.
 */
@Repository
public class SimilarityGraphRepository implements SimilarityGraphStore {

    private final JdbcClient jdbc;

    public SimilarityGraphRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replaceEdges(UUID sourceArtistId, List<Edge> edges) {
        // Delete-then-insert inside one transaction: a re-sync is the provider's current answer in
        // full, so an edge they dropped must disappear rather than linger at its old score.
        jdbc.sql("DELETE FROM recommendation.artist_similarity WHERE source_artist_id = :source")
                .param("source", sourceArtistId)
                .update();
        if (edges.isEmpty()) return;
        jdbc.sql("""
                        INSERT INTO recommendation.artist_similarity
                            (source_artist_id, target_artist_id, score, position, provider, fetched_at)
                        SELECT :source, e.target, e.score, e.position, :provider, now()
                        FROM unnest(CAST(:targets AS uuid[]), CAST(:scores AS float8[]), CAST(:positions AS int[]))
                             AS e(target, score, position)
                        ON CONFLICT (source_artist_id, target_artist_id) DO UPDATE
                            SET score = EXCLUDED.score, position = EXCLUDED.position, fetched_at = now()
                        """)
                .param("source", sourceArtistId)
                .param("provider", Provider.LASTFM.name())
                .param("targets", edges.stream().map(e -> e.targetArtistId().toString()).toArray(String[]::new))
                .param("scores", edges.stream().map(e -> Double.toString(e.score())).toArray(String[]::new))
                .param("positions", edges.stream().map(e -> Integer.toString(e.position())).toArray(String[]::new))
                .update();
    }

    @Override
    @Transactional
    public void replaceTags(UUID artistId, List<ArtistTag> tags) {
        jdbc.sql("DELETE FROM recommendation.artist_tags WHERE artist_id = :artist")
                .param("artist", artistId)
                .update();
        if (tags.isEmpty()) return;
        jdbc.sql("""
                        INSERT INTO recommendation.artist_tags (artist_id, tag, weight, provider, fetched_at)
                        SELECT :artist, t.tag, t.weight, :provider, now()
                        FROM unnest(CAST(:tags AS text[]), CAST(:weights AS int[])) AS t(tag, weight)
                        ON CONFLICT (artist_id, tag) DO UPDATE SET weight = EXCLUDED.weight, fetched_at = now()
                        """)
                .param("artist", artistId)
                .param("provider", Provider.LASTFM.name())
                .param("tags", tags.stream().map(t -> t.tag().toLowerCase(Locale.ROOT)).toArray(String[]::new))
                .param("weights", tags.stream().map(t -> Integer.toString(t.weight())).toArray(String[]::new))
                .update();
    }

    @Override
    public List<Neighbour> neighbours(UUID artistId, int limit, double reverseEdgeFactor) {
        // Both directions in one pass. An artist reachable forwards keeps the forward score (the
        // GROUP BY takes the larger) and is not labelled reverse — bool_and is only true when every
        // row for that artist came from the reverse half.
        return jdbc.sql("""
                        SELECT artist_id, max(score) AS score, bool_and(is_reverse) AS is_reverse
                        FROM (
                            SELECT target_artist_id AS artist_id, score, FALSE AS is_reverse
                              FROM recommendation.artist_similarity WHERE source_artist_id = :artist
                            UNION ALL
                            SELECT source_artist_id, score * :factor, TRUE
                              FROM recommendation.artist_similarity WHERE target_artist_id = :artist
                        ) edges_both_ways
                        GROUP BY artist_id
                        ORDER BY score DESC
                        LIMIT :limit
                        """)
                .param("artist", artistId)
                .param("factor", reverseEdgeFactor)
                .param("limit", limit)
                .query((rs, n) -> new Neighbour((UUID) rs.getObject("artist_id"), rs.getDouble("score"), rs.getBoolean("is_reverse")))
                .list();
    }

    @Override
    public List<ArtistTag> tagsOf(UUID artistId) {
        return jdbc.sql("SELECT tag, weight FROM recommendation.artist_tags WHERE artist_id = :artist ORDER BY weight DESC")
                .param("artist", artistId)
                .query((rs, n) -> new ArtistTag(rs.getString("tag"), rs.getInt("weight")))
                .list();
    }

    @Override
    public List<Neighbour> artistsSharingTags(Collection<String> tags, Collection<UUID> excluding, int limit) {
        if (tags.isEmpty()) return List.of();
        // Score rewards breadth first (how many of the seed's tags an artist carries) and weight
        // second, normalized into the same 0..1 range similarity edges use so the two can be summed.
        return jdbc.sql("""
                        SELECT artist_id,
                               LEAST(sum(weight)::float8 / (100.0 * :tagCount), 1.0) AS score
                        FROM recommendation.artist_tags
                        WHERE tag = ANY(CAST(:tags AS text[]))
                          AND artist_id <> ALL(CAST(:excluding AS uuid[]))
                        GROUP BY artist_id
                        ORDER BY count(*) DESC, sum(weight) DESC
                        LIMIT :limit
                        """)
                .param("tags", tags.stream().map(t -> t.toLowerCase(Locale.ROOT)).toArray(String[]::new))
                .param("excluding", excluding.stream().map(UUID::toString).toArray(String[]::new))
                .param("tagCount", tags.size())
                .param("limit", limit)
                .query((rs, n) -> new Neighbour((UUID) rs.getObject("artist_id"), rs.getDouble("score"), false))
                .list();
    }

    @Override
    public List<Neighbour> artistsByTag(String tag, int limit) {
        return jdbc.sql("""
                        SELECT artist_id, weight / 100.0 AS score
                        FROM recommendation.artist_tags
                        WHERE tag = :tag
                        ORDER BY weight DESC
                        LIMIT :limit
                        """)
                .param("tag", tag.trim().toLowerCase(Locale.ROOT))
                .param("limit", limit)
                .query((rs, n) -> new Neighbour((UUID) rs.getObject("artist_id"), rs.getDouble("score"), false))
                .list();
    }

    @Override
    public List<TagCount> topTags(int limit) {
        return jdbc.sql("""
                        SELECT tag, count(*) AS artists
                        FROM recommendation.artist_tags
                        GROUP BY tag
                        ORDER BY count(*) DESC, tag
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new TagCount(rs.getString("tag"), rs.getInt("artists")))
                .list();
    }

    @Override
    public void recordUnresolved(Collection<String> names) {
        if (names.isEmpty()) return;
        List<String> distinct = names.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (distinct.isEmpty()) return;
        jdbc.sql("""
                        INSERT INTO recommendation.unresolved_artist_names (name_normalized, name, seen_count, last_seen_at)
                        SELECT lower(n.name), n.name, 1, now()
                        FROM unnest(CAST(:names AS text[])) AS n(name)
                        ON CONFLICT (name_normalized) DO UPDATE
                            SET seen_count = recommendation.unresolved_artist_names.seen_count + 1,
                                last_seen_at = now()
                        """)
                .param("names", distinct.toArray(String[]::new))
                .update();
    }

    @Override
    public List<UnresolvedName> unresolved(int limit) {
        return jdbc.sql("""
                        SELECT name, seen_count, last_seen_at
                        FROM recommendation.unresolved_artist_names
                        ORDER BY seen_count DESC, last_seen_at DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new UnresolvedName(rs.getString("name"), rs.getInt("seen_count"),
                        rs.getTimestamp("last_seen_at").toInstant()))
                .list();
    }

    @Override
    public GraphStats stats() {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM recommendation.artist_similarity) AS edges,
                               (SELECT count(DISTINCT source_artist_id) FROM recommendation.artist_similarity) AS with_edges,
                               (SELECT count(DISTINCT artist_id) FROM recommendation.artist_tags) AS tagged,
                               (SELECT count(DISTINCT tag) FROM recommendation.artist_tags) AS tags,
                               (SELECT count(*) FROM recommendation.unresolved_artist_names) AS unresolved
                        """)
                .query((rs, n) -> new GraphStats(rs.getLong("edges"), rs.getLong("with_edges"), rs.getLong("tagged"),
                        rs.getLong("tags"), rs.getLong("unresolved")))
                .single();
    }
}
