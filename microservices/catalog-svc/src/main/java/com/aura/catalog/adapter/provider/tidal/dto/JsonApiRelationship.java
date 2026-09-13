package com.aura.catalog.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON:API relationship object. `data` is a single linkage, an array of linkages, or absent
 * (TIDAL only populates it when the relationship is requested via `include`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonApiRelationship(JsonNode data, JsonNode links) {

    public List<JsonApiResourceId> ids() {
        if (data == null || data.isNull() || data.isMissingNode()) return List.of();
        List<JsonApiResourceId> out = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode n : data) out.add(toId(n));
        } else if (data.isObject()) {
            out.add(toId(data));
        }
        return out;
    }

    private static JsonApiResourceId toId(JsonNode n) {
        return new JsonApiResourceId(n.path("id").asString(), n.path("type").asString());
    }
}
