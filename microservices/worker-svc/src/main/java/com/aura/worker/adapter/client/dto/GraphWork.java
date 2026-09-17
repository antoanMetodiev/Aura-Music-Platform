package com.aura.worker.adapter.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/** Our side of the taste-graph contract with recommendation-svc (see that service's {@code GraphWork}). */
public final class GraphWork {

    private GraphWork() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Claim(UUID artistId, String name, double popularity) {
    }

    public record Similar(String name, double match, String mbid, String url) {
    }

    public record Tag(String name, int count) {
    }

    public record Ingest(List<Similar> similar, List<Tag> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IngestResult(UUID artistId, String name, int edgeCount, int tagCount, int unresolvedCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedResult(int scanned, int added, boolean endOfCatalog) {
    }

    public record Failure(String error) {
    }
}
