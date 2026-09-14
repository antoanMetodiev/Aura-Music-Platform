package com.aura.catalog.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JSON:API relationship object. `data` is a single linkage, an array of linkages, or absent
 * (TIDAL only populates it when the relationship is requested via `include`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonApiRelationship(JsonNode data, JsonNode links) {
    /** Cursor link to the next page of this relationship's ids, when TIDAL has more than it returned inline. */
    public Optional<String> nextLink() {
        if (links == null) return Optional.empty();
        JsonNode next = links.path("next");
        return next.isMissingNode() || next.isNull() ? Optional.empty() : Optional.of(next.asString());
    }


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
