package com.aura.catalog.domain.service;

import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.port.CatalogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Folds provider duplicates of one artist into a canonical row (V14). Two rows with the same name
 * are merged only on evidence they are the same act — a shared recording (ISRC), a shared release
 * (album), or a feature-only profile next to a real catalogue in the same market — never on the
 * name alone, since different artists do share names. Evidence is
 * transitive (A~B, B~C ⇒ one group), and the canonical is the row with the most tracks of its own,
 * then the most popular.
 *
 * <p>Runs after a discography sync (that is when the evidence appears) and over the whole catalog on
 * demand. Idempotent: re-running on a merged group changes nothing.
 */
@Service
public class ArtistMergeService {

    private static final Logger log = LoggerFactory.getLogger(ArtistMergeService.class);

    /** A "feature-only" profile has at most this many tracks... */
    private static final int MAX_FEATURE_ONLY_TRACKS = 5;
    /** ...and folds into a same-named profile with at least this many of its own. */
    private static final int MIN_RICH_TRACKS = 10;

    private final CatalogStore store;

    public ArtistMergeService(CatalogStore store) {
        this.store = store;
    }

    /** Merges the duplicates of this artist's name, if any. Returns the canonical id to serve. */
    public UUID mergeDuplicatesOf(Artist artist) {
        mergeDuplicatesNamed(artist.name());
        return store.findArtistById(artist.id()).map(Artist::canonicalId).orElse(artist.canonicalId());
    }

    /** Merges every row carrying this name that has evidence of being the same artist. */
    public void mergeDuplicatesNamed(String name) {
        List<Artist> sameName = store.findArtistsByNormalizedName(name);
        if (sameName.size() >= 2) mergeGroup(sameName);
    }

    /** Every duplicated name in the catalog. Returns how many rows became aliases. */
    public int mergeAll() {
        int merged = 0;
        for (String name : store.findDuplicatedArtistNames()) {
            merged += mergeGroup(store.findArtistsByNormalizedName(name));
        }
        log.info("Artist duplicate merge: {} rows folded into canonical artists", merged);
        return merged;
    }

    /** Union-find over the same-named rows; returns how many rows were (newly or still) aliases. */
    private int mergeGroup(List<Artist> rows) {
        Map<UUID, UUID> parent = new HashMap<>();
        for (Artist a : rows) parent.put(a.id(), a.id());
        // Existing links count as evidence already established.
        for (Artist a : rows) {
            if (a.isAlias() && parent.containsKey(a.canonicalArtistId())) union(parent, a.id(), a.canonicalArtistId());
        }
        for (int i = 0; i < rows.size(); i++) {
            for (int j = i + 1; j < rows.size(); j++) {
                UUID a = rows.get(i).id();
                UUID b = rows.get(j).id();
                if (find(parent, a).equals(find(parent, b))) continue;
                if (store.artistsShareRecordingOrRelease(a, b) || isSplitOffFeature(a, b) || isSplitOffFeature(b, a)) union(parent, a, b);
            }
        }

        Map<UUID, List<Artist>> groups = new LinkedHashMap<>();
        for (Artist a : rows) groups.computeIfAbsent(find(parent, a.id()), k -> new ArrayList<>()).add(a);

        int aliases = 0;
        Map<UUID, Integer> ownTracks = store.countOwnTracksByArtist(rows.stream().map(Artist::id).toList());
        for (List<Artist> group : groups.values()) {
            if (group.size() < 2) continue;
            Artist canonical = group.stream()
                    .max(Comparator.<Artist>comparingInt(a -> ownTracks.getOrDefault(a.id(), 0))
                            .thenComparingDouble(Artist::popularity))
                    .orElseThrow();
            List<UUID> others = group.stream().map(Artist::id).filter(id -> !id.equals(canonical.id())).toList();
            boolean changed = group.stream().anyMatch(a -> a.id().equals(canonical.id()) ? a.isAlias() : !canonical.id().equals(a.canonicalArtistId()));
            if (changed) {
                store.setCanonicalArtist(others, canonical.id());
                log.info("Merged {} duplicate rows of '{}' into {} ({} own tracks)", others.size(), canonical.name(),
                        canonical.id(), ownTracks.getOrDefault(canonical.id(), 0));
            }
            aliases += others.size();
        }
        return aliases;
    }

    /**
     * The other way a provider splits an artist: a guest appearance uploaded by the collaborator's
     * label under a fresh profile. That profile has a handful of tracks, is the primary artist on
     * none and owns no album, while a same-named profile has a real catalogue in the same market
     * (an ISRC issued in the same country). Nothing they release overlaps, so the recording/release
     * rule can't see it; this one can.
     */
    private boolean isSplitOffFeature(UUID sparse, UUID rich) {
        return store.isFeatureOnlyArtistProfile(sparse, MAX_FEATURE_ONLY_TRACKS)
                && store.countOwnTracksByArtist(List.of(rich)).getOrDefault(rich, 0) >= MIN_RICH_TRACKS
                && store.artistsShareIsrcCountry(sparse, rich);
    }

    private static UUID find(Map<UUID, UUID> parent, UUID id) {
        UUID p = parent.get(id);
        while (!p.equals(id)) {
            id = p;
            p = parent.get(id);
        }
        return id;
    }

    private static void union(Map<UUID, UUID> parent, UUID a, UUID b) {
        parent.put(find(parent, a), find(parent, b));
    }
}
