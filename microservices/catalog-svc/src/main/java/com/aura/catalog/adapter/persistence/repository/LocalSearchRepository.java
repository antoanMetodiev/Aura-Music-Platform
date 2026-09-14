package com.aura.catalog.adapter.persistence.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Full-text search over our own catalog (Project-Info.md §31), backed by the pg_trgm extension
 * from the V4 migration. Every word of the query must appear somewhere in "title + artist name(s)",
 * in any order — so "lana rey", "rey lana" and "sandman metallica" all work — ranked by trigram
 * similarity to the whole query, then popularity. Returns ids only; callers hydrate through the
 * batch {@code findByIds} readers.
 */
@Repository
public class LocalSearchRepository {

    private static final String TRACK_HAYSTACK = """
            t.title || ' ' || COALESCE((SELECT string_agg(a.name, ' ')
                                        FROM catalog.track_artists ta
                                        JOIN catalog.artists a ON a.id = ta.artist_id
                                        WHERE ta.track_id = t.id), '')""";
    private static final String ALBUM_HAYSTACK = "al.title || ' ' || COALESCE(a.name, '')";
    private static final String ARTIST_HAYSTACK = "name";

    private final JdbcClient jdbc;

    public LocalSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> tracks(String query, int limit) {
        return run("catalog.tracks t", TRACK_HAYSTACK, "t.id", "t.popularity", query, limit);
    }

    public List<UUID> albums(String query, int limit) {
        return run("catalog.albums al LEFT JOIN catalog.artists a ON a.id = al.artist_id",
                ALBUM_HAYSTACK, "al.id", "al.popularity", query, limit);
    }

    public List<UUID> artists(String query, int limit) {
        return run("catalog.artists", ARTIST_HAYSTACK, "id", "popularity", query, limit);
    }

    private List<UUID> run(String from, String haystack, String idColumn, String popularityColumn, String query, int limit) {
        List<String> words = Arrays.stream(query.trim().split("\\s+")).filter(w -> !w.isEmpty()).toList();
        if (words.isEmpty()) return List.of();

        StringBuilder sql = new StringBuilder("SELECT ").append(idColumn).append(" FROM ").append(from).append(" WHERE ");
        for (int i = 0; i < words.size(); i++) {
            sql.append(i == 0 ? "" : " AND ").append("(").append(haystack).append(") ILIKE :w").append(i);
        }
        sql.append(" ORDER BY extensions.similarity(").append(haystack).append(", :q) DESC, ")
                .append(popularityColumn).append(" DESC LIMIT :limit");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("q", query.trim()).param("limit", limit);
        for (int i = 0; i < words.size(); i++) {
            spec = spec.param("w" + i, containsPattern(words.get(i)));
        }
        return spec.query(UUID.class).list();
    }

    /** Escapes LIKE wildcards in user input, then wraps for a contains-match. */
    private static String containsPattern(String word) {
        return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
