package com.aura.playback.adapter.web;

import com.aura.playback.adapter.web.dto.PlaybackSourceResponse;
import com.aura.playback.domain.model.PlaybackSource;
import com.aura.playback.domain.service.PlaybackResolverService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public playback API (Project-Info.md §10 mounts this behind the gateway at the same path).
 * One endpoint for now: resolve a catalog track to a playable YouTube video id.
 */
@RestController
@RequestMapping("/api/v1/playback")
public class PlaybackController {

    private final PlaybackResolverService resolver;

    public PlaybackController(PlaybackResolverService resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/tracks/{trackId}/source")
    public PlaybackSourceResponse resolve(@PathVariable UUID trackId) {
        PlaybackSource source = resolver.resolve(trackId);
        return new PlaybackSourceResponse(
                source.trackId(),
                source.provider().name(),
                source.providerResourceId(),
                source.title(),
                source.channelTitle(),
                source.durationMs()
        );
    }
}
