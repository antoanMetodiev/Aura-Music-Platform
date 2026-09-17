package com.aura.recommendation.adapter.provider.playback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Only the field we need from playback-svc's response: which track it is. Playable is "a row exists". */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaybackSourceResponse(UUID trackId) {
}
