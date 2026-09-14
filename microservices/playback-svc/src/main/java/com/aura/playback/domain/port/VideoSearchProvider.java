package com.aura.playback.domain.port;

import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.VideoCandidate;

import java.util.List;

/**
 * Finds playback candidates for a canonical track (Project-Info.md §16, §17). Business logic
 * (matching/scoring) never depends on this directly through a concrete class — only through this
 * port — so a second provider can be added later without touching {@code PlaybackResolverService}.
 */
public interface VideoSearchProvider {

    PlaybackProvider provider();

    List<VideoCandidate> search(CanonicalTrack track);

    /** One specific video by provider id, hydrated the same way search results are (duration, embeddability). Empty if gone. */
    java.util.Optional<VideoCandidate> findById(String providerResourceId);
}
