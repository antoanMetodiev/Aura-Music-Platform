package com.aura.catalog.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

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
}
