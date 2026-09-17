package com.aura.recommendation.adapter.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * The wire contract between recommendation-svc and worker-svc for the taste-graph build.
 *
 * <p>The worker sends the provider's answer as it came — <em>names</em>, matches and tags — and this
 * service decides what those names mean. Resolving them needs the catalog and our own spelling rules,
 * so it stays here; the worker owns the credentials and the pacing, nothing about the domain.
 */
public final class GraphWork {

    private GraphWork() {
    }

    public record Claim(UUID artistId, String name, double popularity) {
    }

    /** @param match the provider's own similarity weight, 0..1 */
    public record Similar(String name, double match, String mbid, String url) {
    }

    /** @param count the provider's tag weight, 0..100 */
    public record Tag(String name, int count) {
    }

    public record Ingest(List<Similar> similar, List<Tag> tags) {
    }

    /**
     * @param unresolvedCount names the provider gave that our catalog doesn't have — not an error,
     *                        a wishlist (see {@code /discovery/unresolved})
     */
    public record IngestResult(UUID artistId, String name, int edgeCount, int tagCount, int unresolvedCount) {
    }

    public record SeedResult(int scanned, int added, boolean endOfCatalog) {
    }

    public record Failure(String error) {
    }
}
