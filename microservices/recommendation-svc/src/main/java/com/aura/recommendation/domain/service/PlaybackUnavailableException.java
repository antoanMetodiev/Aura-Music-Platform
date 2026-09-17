package com.aura.recommendation.domain.service;

/**
 * playback-svc could not be reached. Recoverable by design: a feed that cannot check playability is
 * served unfiltered rather than not served (Project-Info.md §48).
 */
public class PlaybackUnavailableException extends RuntimeException {

    public PlaybackUnavailableException(Throwable cause) {
        super("playback-svc is currently unavailable", cause);
    }
}
