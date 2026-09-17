package com.aura.recommendation.domain.model;

import java.util.UUID;

/**
 * An artist as catalog-svc hands them to us. This service owns no artist data of its own — only the
 * edges between artists — so the catalog's shape is carried through unchanged (integration contract,
 * Project-Info.md §6) rather than copied into a local model that would drift.
 */
public record ArtistRef(UUID id, String name, Artwork artwork, double popularity) {
}
