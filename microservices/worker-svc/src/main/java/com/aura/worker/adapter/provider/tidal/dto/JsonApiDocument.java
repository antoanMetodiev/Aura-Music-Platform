package com.aura.worker.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Top-level JSON:API document. `data` is a resource or an array of resources. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonApiDocument(JsonNode data, List<JsonApiResource> included, JsonNode links) {

    public List<JsonApiResource> dataResources(ObjectMapper mapper) {
        if (data == null || data.isNull() || data.isMissingNode()) return List.of();
        List<JsonApiResource> out = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode n : data) out.add(mapper.treeToValue(n, JsonApiResource.class));
        } else {
            out.add(mapper.treeToValue(data, JsonApiResource.class));
        }
        return out;
    }

    public List<JsonApiResource> includedResources() {
        return included == null ? List.of() : included;
    }

    /** For relationship pages, whose `data` is a list of bare `{id, type}` identifiers rather than resources. */
    public List<JsonApiResourceId> dataIds() {
        return dataLinkages().stream().map(JsonApiLinkage::id).toList();
    }

    /** Same as {@link #dataIds()} but keeps each linkage's {@code meta} (album items carry their position there). */
    public List<JsonApiLinkage> dataLinkages() {
        if (data == null || !data.isArray()) return List.of();
        List<JsonApiLinkage> out = new ArrayList<>();
        for (JsonNode n : data) {
            JsonNode meta = n.path("meta");
            out.add(new JsonApiLinkage(
                    new JsonApiResourceId(n.path("id").asString(), n.path("type").asString()),
                    meta.isMissingNode() || meta.isNull() ? null : meta));
        }
        return out;
    }

    public Optional<String> nextLink() {
        if (links == null) return Optional.empty();
        JsonNode next = links.path("next");
        return next.isMissingNode() || next.isNull() ? Optional.empty() : Optional.of(next.asString());
    }
}
