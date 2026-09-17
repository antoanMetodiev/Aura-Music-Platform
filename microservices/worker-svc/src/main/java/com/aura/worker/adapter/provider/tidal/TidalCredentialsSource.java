package com.aura.worker.adapter.provider.tidal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * This service's own TIDAL credentials, from the environment and nowhere else.
 *
 * <p>catalog-svc reads its keys from {@code catalog.tidal_api_keys} and rotates them; this one
 * deliberately does not look at that table. The whole reason the background work moved out of
 * catalog-svc is that it was spending the credentials the user-facing requests needed — sharing the
 * pool again would put that back exactly as it was, with an extra network hop for company.
 *
 * <p>So: one key, owned by this process, and a rate-limit budget nobody else can spend. If TIDAL
 * counts its limit per client, the two sides now genuinely have separate budgets; if it counts per
 * IP, this service can at least be moved to its own host, which it could not while it lived inside
 * catalog-svc.
 */
@Component
public class TidalCredentialsSource {

    private static final Logger log = LoggerFactory.getLogger(TidalCredentialsSource.class);

    public record Credentials(UUID id, String label, String clientId, String clientSecret) {
    }

    private final Credentials credentials;

    public TidalCredentialsSource(TidalProperties properties) {
        this.credentials = new Credentials(null, "worker-env", properties.clientId(), properties.clientSecret());
        if (!properties.configured()) {
            log.warn("TIDAL credentials not configured — the discography worker cannot run; set TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET");
        }
    }

    public Credentials current() {
        return credentials;
    }

    /** One key, so there is never another to move to — kept for the shape the auth client expects. */
    public int poolSize() {
        return 1;
    }

    public void penalize(Credentials failed, String reason) {
        log.debug("TIDAL key '{}' failed ({}), no other key to switch to", failed.label(), reason);
    }

    public void markUsed(Credentials used) {
        // Nothing to record: the key is static configuration here, not a row with a last-used stamp.
    }
}
