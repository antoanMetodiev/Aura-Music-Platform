package com.aura.playback.domain.service;

import java.util.UUID;

/** The given track id does not exist in catalog-svc. */
public class TrackNotFoundException extends RuntimeException {

    public TrackNotFoundException(UUID trackId) {
        super("Track " + trackId + " was not found in the catalog");
    }
}
