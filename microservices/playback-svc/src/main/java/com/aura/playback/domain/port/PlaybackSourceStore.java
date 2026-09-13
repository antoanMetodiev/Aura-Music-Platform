package com.aura.playback.domain.port;

import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.PlaybackSource;

import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@code playback.track_sources} (Project-Info.md §19). */
public interface PlaybackSourceStore {

    Optional<PlaybackSource> findByTrackId(UUID trackId, PlaybackProvider provider);

    PlaybackSource upsert(PlaybackSource source);
}
