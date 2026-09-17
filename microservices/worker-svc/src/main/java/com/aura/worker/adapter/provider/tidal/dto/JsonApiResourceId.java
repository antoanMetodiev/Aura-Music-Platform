package com.aura.worker.adapter.provider.tidal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** JSON:API resource linkage: {"id": "…", "type": "tracks"}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonApiResourceId(String id, String type) {

    public String key() {
        return type + ":" + id;
    }
}
