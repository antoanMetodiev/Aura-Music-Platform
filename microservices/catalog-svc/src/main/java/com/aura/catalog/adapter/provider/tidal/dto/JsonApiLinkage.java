package com.aura.catalog.adapter.provider.tidal.dto;

import tools.jackson.databind.JsonNode;

/** JSON:API resource linkage plus its per-relationship {@code meta} (e.g. an album item's volume/track number). */
public record JsonApiLinkage(JsonApiResourceId id, JsonNode meta) {

    public Integer metaInt(String field) {
        if (meta == null) return null;
        JsonNode value = meta.path(field);
        return value.isNumber() ? value.asInt() : null;
    }
}
