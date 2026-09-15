package com.aura.catalog.adapter.persistence.repository;

import com.aura.catalog.domain.model.ArtistAbout;
import com.aura.catalog.domain.model.ArtistAbout.Biography;
import com.aura.catalog.domain.model.ArtistAbout.ExternalLink;
import com.aura.catalog.domain.model.ArtistAbout.SimilarArtist;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.ArtistAboutStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code catalog.artist_about} — see V13 migration. One row per artist; all-null columns = recorded miss. */
@Repository
public class ArtistAboutRepository implements ArtistAboutStore {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<StoredSimilar>> SIMILAR = new TypeReference<>() {
    };
    private static final TypeReference<List<ExternalLink>> LINKS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public ArtistAboutRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<ArtistAbout> find(UUID artistId) {
        return jdbc.sql("SELECT * FROM catalog.artist_about WHERE artist_id = :id")
                .param("id", artistId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void save(ArtistAbout about) {
        Biography bio = about.biography();
        jdbc.sql("""
                        INSERT INTO catalog.artist_about
                            (artist_id, bio, bio_source, bio_url, bio_lang, listeners, playcount, tags, similar_artists, links, fetched_at)
                        VALUES (:id, :bio, :bioSource, :bioUrl, :bioLang, :listeners, :playcount,
                                CAST(:tags AS jsonb), CAST(:similar AS jsonb), CAST(:links AS jsonb), :fetchedAt)
                        ON CONFLICT (artist_id) DO UPDATE SET
                            bio = EXCLUDED.bio, bio_source = EXCLUDED.bio_source, bio_url = EXCLUDED.bio_url,
                            bio_lang = EXCLUDED.bio_lang, listeners = EXCLUDED.listeners, playcount = EXCLUDED.playcount,
                            tags = EXCLUDED.tags, similar_artists = EXCLUDED.similar_artists, links = EXCLUDED.links,
                            fetched_at = EXCLUDED.fetched_at
                        """)
                .param("id", about.artistId())
                .param("bio", bio == null ? null : bio.text())
                .param("bioSource", bio == null ? null : bio.source().name())
                .param("bioUrl", bio == null ? null : bio.url())
                .param("bioLang", bio == null ? null : bio.language())
                .param("listeners", about.listeners(), java.sql.Types.BIGINT)
                .param("playcount", about.playcount(), java.sql.Types.BIGINT)
                .param("tags", json.writeValueAsString(about.tags()))
                // Similar artists are stored without the catalog match — that is re-done on every read, since our catalog keeps growing.
                .param("similar", json.writeValueAsString(about.similar().stream().map(s -> new StoredSimilar(s.name(), s.url())).toList()))
                .param("links", json.writeValueAsString(about.links()))
                .param("fetchedAt", Timestamp.from(about.fetchedAt()))
                .update();
    }

    private ArtistAbout mapRow(ResultSet rs, int rowNum) throws SQLException {
        String bioText = rs.getString("bio");
        Biography bio = bioText == null ? null
                : new Biography(bioText, Provider.valueOf(rs.getString("bio_source")), rs.getString("bio_url"), rs.getString("bio_lang"));
        return new ArtistAbout(
                rs.getObject("artist_id", UUID.class),
                bio,
                rs.getObject("listeners", Long.class),
                rs.getObject("playcount", Long.class),
                readList(rs.getString("tags"), STRINGS),
                readList(rs.getString("similar_artists"), SIMILAR).stream().map(s -> new SimilarArtist(s.name(), s.url(), null)).toList(),
                readList(rs.getString("links"), LINKS),
                rs.getTimestamp("fetched_at").toInstant()
        );
    }

    private <T> List<T> readList(String column, TypeReference<List<T>> type) {
        return column == null ? List.of() : json.readValue(column, type);
    }

    /** On-disk shape of one similar artist — the catalog match is never stored. */
    private record StoredSimilar(String name, String url) {
    }
}
