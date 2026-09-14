package com.aura.catalog.adapter.provider.tidal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Which TIDAL client credentials to use right now. Enabled rows of {@code catalog.tidal_api_keys}
 * win over the env-configured pair; with several rows the service rotates through them in
 * {@code priority} order, and {@link #penalize} benches a key that just got rate-limited or
 * rejected so the next call goes out on a different one.
 */
@Component
public class TidalCredentialsSource {

    private static final Logger log = LoggerFactory.getLogger(TidalCredentialsSource.class);
    private static final Duration RELOAD_INTERVAL = Duration.ofMinutes(1);
    private static final Duration PENALTY = Duration.ofMinutes(5);

    public record Credentials(UUID id, String label, String clientId, String clientSecret) {
        boolean fromDatabase() {
            return id != null;
        }
    }

    private final JdbcClient jdbc;
    private final TidalProperties properties;
    private final Clock clock;

    private volatile List<Credentials> pool = List.of();
    private volatile Instant loadedAt = Instant.EPOCH;
    private volatile int cursor = 0;

    public TidalCredentialsSource(JdbcClient jdbc, TidalProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public Credentials current() {
        List<Credentials> active = activePool();
        return active.get(Math.floorMod(cursor, active.size()));
    }

    /** Moves to the next key and benches the current one for a while. No-op with a single key. */
    public synchronized void penalize(Credentials failed, String reason) {
        if (failed.fromDatabase()) {
            jdbc.sql("UPDATE catalog.tidal_api_keys SET disabled_until = :until WHERE id = :id")
                    .param("until", clock.instant().plus(PENALTY))
                    .param("id", failed.id())
                    .update();
            loadedAt = Instant.EPOCH;
        }
        List<Credentials> active = activePool();
        if (active.size() > 1) {
            cursor++;
            log.warn("TIDAL key '{}' benched ({}), switching to '{}'", failed.label(), reason, current().label());
        } else {
            log.debug("TIDAL key '{}' failed ({}), no other key to switch to", failed.label(), reason);
        }
    }

    public void markUsed(Credentials credentials) {
        if (!credentials.fromDatabase()) return;
        jdbc.sql("UPDATE catalog.tidal_api_keys SET last_used_at = now() WHERE id = :id")
                .param("id", credentials.id())
                .update();
    }

    public int poolSize() {
        return activePool().size();
    }

    private List<Credentials> activePool() {
        if (clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) reload();
        return pool;
    }

    private synchronized void reload() {
        if (!clock.instant().isAfter(loadedAt.plus(RELOAD_INTERVAL))) return;
        record Row(Credentials credentials, boolean benched) {
        }
        List<Row> enabled = jdbc.sql("""
                        SELECT id, label, client_id, client_secret,
                               (disabled_until IS NOT NULL AND disabled_until > now()) AS benched
                        FROM catalog.tidal_api_keys
                        WHERE enabled
                        ORDER BY priority, created_at
                        """)
                .query((rs, n) -> new Row(new Credentials((UUID) rs.getObject("id"), rs.getString("label"),
                        rs.getString("client_id"), rs.getString("client_secret")), rs.getBoolean("benched")))
                .list();
        // A benched key is still better than no key at all — only skip it while an alternative exists.
        List<Credentials> fromDb = enabled.stream().filter(r -> !r.benched()).map(Row::credentials).toList();
        if (fromDb.isEmpty()) fromDb = enabled.stream().map(Row::credentials).toList();

        if (!fromDb.isEmpty()) {
            pool = fromDb;
        } else if (properties.clientId() != null && !properties.clientId().isBlank()
                && properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            pool = List.of(new Credentials(null, "env", properties.clientId(), properties.clientSecret()));
        } else {
            throw new IllegalStateException(
                    "No TIDAL credentials: add a row to catalog.tidal_api_keys or set TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET");
        }
        loadedAt = clock.instant();
    }
}
