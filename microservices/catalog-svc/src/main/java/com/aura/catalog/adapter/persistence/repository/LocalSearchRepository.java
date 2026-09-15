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

    // ── Type-ahead ─────────────────────────────────────────────────────────────────────────

    /**
     * Tracks for the suggestion dropdown: every word must appear in the title <em>or</em> in one of
     * the track's artist names (each side index-backed, unlike the concatenated haystack above), and
     * titles that <em>start</em> with the query come first — that's what a person typing expects.
     */
    public List<UUID> suggestTracks(String query, int limit) {
        List<String> words = words(query);
        if (words.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder("SELECT t.id FROM catalog.tracks t WHERE ");
        for (int i = 0; i < words.size(); i++) {
            sql.append(i == 0 ? "" : " AND ")
                    .append("(t.title ILIKE :w").append(i)
                    .append(" OR EXISTS (SELECT 1 FROM catalog.track_artists ta JOIN catalog.artists a ON a.id = ta.artist_id")
                    .append(" WHERE ta.track_id = t.id AND a.name ILIKE :w").append(i).append("))");
        }
        // Ranking: for a single word, anything that *starts* with it (title or an artist name) first
        // — that's what someone mid-word expects. Then how close "title + artists" is to the whole
        // query, with popularity added so that among equally good matches the well-known recording
        // wins over an obscure cover. (For several words a prefix on the title is misleading —
        // "adele hel" must find "Hello / Adele", not a remix titled "Adele Hello…".)
        sql.append(" ORDER BY ");
        if (words.size() == 1) {
            sql.append("(lower(t.title) LIKE :prefix OR EXISTS (SELECT 1 FROM catalog.track_artists ta JOIN catalog.artists a ON a.id = ta.artist_id")
                    .append(" WHERE ta.track_id = t.id AND lower(a.name) LIKE :prefix)) DESC, ");
        }
        sql.append("extensions.similarity(").append(TRACK_HAYSTACK).append(", :q) + t.popularity DESC LIMIT :limit");
        return bind(jdbc.sql(sql.toString()), words, query, limit).query(UUID.class).list();
    }

    public List<UUID> suggestArtists(String query, int limit) {
        List<String> words = words(query);
        if (words.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder("SELECT id FROM catalog.artists WHERE ");
        for (int i = 0; i < words.size(); i++) {
            sql.append(i == 0 ? "" : " AND ").append("name ILIKE :w").append(i);
        }
        sql.append(" ORDER BY ");
        if (words.size() == 1) sql.append("(lower(name) LIKE :prefix) DESC, ");
        sql.append("extensions.similarity(name, :q) + popularity DESC LIMIT :limit");
        return bind(jdbc.sql(sql.toString()), words, query, limit).query(UUID.class).list();
    }

    private static List<String> words(String query) {
        return Arrays.stream(query.trim().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, List<String> words, String query, int limit) {
        String q = query.trim();
        spec = spec.param("q", q).param("prefix", escapeLike(q.toLowerCase()) + "%").param("limit", limit);
        for (int i = 0; i < words.size(); i++) {
            spec = spec.param("w" + i, containsPattern(words.get(i)));
        }
        return spec;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
