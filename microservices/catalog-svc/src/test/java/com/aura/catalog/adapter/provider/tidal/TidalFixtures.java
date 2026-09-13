package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/** Loads recorded TIDAL JSON:API responses from {@code src/test/resources/tidal/} for adapter tests. */
final class TidalFixtures {

    private TidalFixtures() {
    }

    static JsonApiDocument load(ObjectMapper mapper, String resourceName) {
        try (InputStream in = TidalFixtures.class.getResourceAsStream("/tidal/" + resourceName)) {
            if (in == null) throw new IllegalStateException("Missing test fixture: tidal/" + resourceName);
            return mapper.readValue(in, JsonApiDocument.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
