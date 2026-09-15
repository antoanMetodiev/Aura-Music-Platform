package com.aura.playback.domain.port;

import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.PlaybackSource;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@code playback.track_sources} (Project-Info.md §19). */
public interface PlaybackSourceStore {

    Optional<PlaybackSource> findByTrackId(UUID trackId, PlaybackProvider provider);

    /**
     * A verified source recorded for <em>any</em> track carrying this ISRC (highest score first). Same
     * ISRC = same recording, so it is the right video for every re-release of it too.
     */
    Optional<PlaybackSource> findVerifiedByIsrc(String isrc, PlaybackProvider provider);

    PlaybackSource upsert(PlaybackSource source);
}
