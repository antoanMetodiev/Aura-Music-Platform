package com.aura.playback.domain.service;

import java.util.UUID;

/**
 * No candidate cleared even the medium-confidence threshold. Project-Info.md §18: "playback
 * unavailable" is always preferable to a wrong version of the song, so the resolver refuses to
 * pick a low-confidence result just because it's the only one.
 */
public class NoConfidentPlaybackMatchException extends RuntimeException {

    public NoConfidentPlaybackMatchException(UUID trackId) {
        super("No confident playback source found for track " + trackId);
    }
}
