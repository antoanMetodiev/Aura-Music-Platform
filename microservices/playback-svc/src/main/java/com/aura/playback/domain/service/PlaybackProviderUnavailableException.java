package com.aura.playback.domain.service;

import com.aura.playback.domain.model.PlaybackProvider;

/** Raised when a video provider call fails after retries / the circuit is open. */
public class PlaybackProviderUnavailableException extends RuntimeException {

    public PlaybackProviderUnavailableException(PlaybackProvider provider, Throwable cause) {
        super(provider + " is currently unavailable", cause);
    }
}
