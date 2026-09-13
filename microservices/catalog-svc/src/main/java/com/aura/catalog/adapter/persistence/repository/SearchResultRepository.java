package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.CachedSearch;
import com.aura.catalog.domain.model.SearchType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code catalog.search_results} — see V2 migration. One row per (normalized query, type). */
@Repository
public class SearchResultRepository {

    private final JdbcClient jdbc;

    public SearchResultRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CachedSearch> find(String normalizedQuery, SearchType type) {
        return jdbc.sql("SELECT * FROM catalog.search_results WHERE query_normalized = :q AND search_type = :type")
                .param("q", normalizedQuery)
                .param("type", type.name())
                .query(SearchResultRepository::mapRow)
                .optional();
    }

    public void save(String normalizedQuery, SearchType type, List<UUID> entityIds) {
        // Bound as text[] and cast: pgjdbc encodes String[] natively, UUID[] it does not.
        String[] ids = entityIds.stream().map(UUID::toString).toArray(String[]::new);
        jdbc.sql("""
                        INSERT INTO catalog.search_results (query_normalized, search_type, entity_ids, fetched_at)
                        VALUES (:q, :type, CAST(:ids AS uuid[]), now())
                        ON CONFLICT (query_normalized, search_type) DO UPDATE SET
                            entity_ids = EXCLUDED.entity_ids,
                            fetched_at = EXCLUDED.fetched_at
                        """)
                .param("q", normalizedQuery)
                .param("type", type.name())
                .param("ids", ids)
                .update();
    }

    private static CachedSearch mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID[] ids = (UUID[]) rs.getArray("entity_ids").getArray();
        return new CachedSearch(
                rs.getString("query_normalized"),
                SearchType.valueOf(rs.getString("search_type")),
                Arrays.asList(ids),
                rs.getTimestamp("fetched_at").toInstant()
        );
    }
}
