package com.aura.recommendation.domain.port;

import com.aura.recommendation.domain.model.ArtistTag;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence port for the graph itself: {@code recommendation.artist_similarity} and {@code artist_tags}. */
public interface SimilarityGraphStore {

    /** One resolved edge, ready to be written. */
    record Edge(UUID sourceArtistId, UUID targetArtistId, double score, int position) {
    }

    /** A neighbour as the graph has it, artist not hydrated yet. */
    record Neighbour(UUID artistId, double score, boolean reverse) {
    }

    record TagCount(String tag, int artistCount) {
    }

    record UnresolvedName(String name, int seenCount, Instant lastSeenAt) {
    }

    record GraphStats(long edges, long artistsWithEdges, long taggedArtists, long distinctTags, long unresolvedNames) {
    }

    /** An artist's edges are replaced wholesale — a re-sync is the provider's current answer, not an addition. */
    void replaceEdges(UUID sourceArtistId, List<Edge> edges);

    void replaceTags(UUID artistId, List<ArtistTag> tags);

    /**
     * Neighbours of {@code artistId} in both directions: artists the provider called similar to this
     * one, plus artists this one was called similar to. Strongest first, at most {@code limit}, with
     * reverse edges discounted by {@code reverseEdgeFactor} — that discount is what keeps a huge
     * artist from dominating every small artist's radio while still letting the small one be found.
     */
    List<Neighbour> neighbours(UUID artistId, int limit, double reverseEdgeFactor);

    List<ArtistTag> tagsOf(UUID artistId);

    /**
     * Artists carrying any of {@code tags}, scored by how heavily and how many they share, excluding
     * {@code excluding}. The genre axis: how an artist with no similarity edges is still reachable.
     */
    List<Neighbour> artistsSharingTags(Collection<String> tags, Collection<UUID> excluding, int limit);

    /** Artists carrying one tag, heaviest first — the genre browse. */
    List<Neighbour> artistsByTag(String tag, int limit);

    /** The most common tags across the graph, for a genre grid. */
    List<TagCount> topTags(int limit);

    /** Records that the provider named an artist we don't have. Bumps {@code seen_count} when already known. */
    void recordUnresolved(Collection<String> names);

    List<UnresolvedName> unresolved(int limit);

    GraphStats stats();
}
