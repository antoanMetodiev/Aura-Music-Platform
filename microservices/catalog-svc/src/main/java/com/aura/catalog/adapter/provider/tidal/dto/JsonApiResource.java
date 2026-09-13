package com.aura.catalog.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** JSON:API resource object. Attributes stay untyped until the mapper knows the `type`. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonApiResource(String id, String type, JsonNode attributes, Map<String, JsonApiRelationship> relationships) {

    public String key() {
        return type + ":" + id;
    }

    public List<JsonApiResourceId> related(String relationship) {
        if (relationships == null) return List.of();
        JsonApiRelationship rel = relationships.get(relationship);
        return rel == null ? List.of() : rel.ids();
    }
}
