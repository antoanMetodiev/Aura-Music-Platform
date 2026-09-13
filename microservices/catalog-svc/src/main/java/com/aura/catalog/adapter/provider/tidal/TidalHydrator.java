package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResourceId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared "collect referenced ids, batch-hydrate, merge back into the index" plumbing.
 *
 * TIDAL's documented {@code include} support is top-level only, so a track fetched with
 * {@code include=albums} gets a title-only album (no cover art, no album artist). Both
 * {@link TidalMetadataProvider} and {@link TidalSearchProvider} fix this the same way: collect the
 * distinct album/artist ids referenced by what they already have, batch-fetch those ids with a
 * fuller {@code include}, and merge the richer resources back into the same {@link ResourceIndex}
 * (later entries overwrite the partial ones — see {@link ResourceIndex#add}).
 */
final class TidalHydrator {

    private TidalHydrator() {
    }

    /** Distinct ids of `relationship` linked from every resource of `fromType` currently in the index. */
    static Set<String> referencedIds(ResourceIndex index, String fromType, String relationship) {
        Set<String> ids = new LinkedHashSet<>();
        for (JsonApiResource resource : index.ofType(fromType)) {
            for (JsonApiResourceId ref : resource.related(relationship)) {
                ids.add(ref.id());
            }
        }
        return ids;
    }

    /** Splits into batches no larger than {@code size} (TIDAL caps {@code filter[id]} at 20 per call). */
    static List<List<String>> chunks(Set<String> ids, int size) {
        List<String> all = new ArrayList<>(ids);
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += size) {
            out.add(all.subList(i, Math.min(i + size, all.size())));
        }
        return out;
    }
}
