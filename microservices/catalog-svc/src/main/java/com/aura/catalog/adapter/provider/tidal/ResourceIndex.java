package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResourceId;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flat lookup of every JSON:API resource we've fetched for one operation, keyed by {@code type:id}.
 * Lets the mapper resolve relationships (track → album → cover art) across several responses.
 */
final class ResourceIndex {

    private final Map<String, JsonApiResource> byKey = new HashMap<>();

    void add(JsonApiDocument document, ObjectMapper mapper) {
        if (document == null) return;
        document.dataResources(mapper).forEach(r -> byKey.put(r.key(), r));
        document.includedResources().forEach(r -> byKey.put(r.key(), r));
    }

    Optional<JsonApiResource> get(JsonApiResourceId id) {
        return Optional.ofNullable(byKey.get(id.key()));
    }

    Optional<JsonApiResource> get(String type, String id) {
        return Optional.ofNullable(byKey.get(type + ":" + id));
    }

    boolean contains(JsonApiResourceId id) {
        return byKey.containsKey(id.key());
    }

    List<JsonApiResource> ofType(String type) {
        return byKey.values().stream().filter(r -> type.equals(r.type())).toList();
    }
}
