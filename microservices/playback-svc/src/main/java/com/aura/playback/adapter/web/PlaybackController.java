package com.aura.playback.adapter.web;

import com.aura.playback.adapter.web.dto.PlaybackSourceResponse;
import com.aura.playback.domain.model.PlaybackSource;
import com.aura.playback.domain.service.PlaybackResolverService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
        return toResponse(resolver.resolve(trackId));
    }

    /**
     * Which of these tracks are already playable — the verified sources among them, in no particular
     * order, tracks without one simply absent. Read-only: unlike {@link #resolve}, nothing is resolved
     * as a side effect, so no provider quota is ever spent here (Project-Info.md §20). Callers that
     * rank many tracks at once (recommendation-svc) use it to drop the unplayable ones.
     */
    @GetMapping("/sources")
    public List<PlaybackSourceResponse> verifiedSources(@RequestParam("trackIds") List<UUID> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            throw new IllegalArgumentException("'trackIds' must not be empty");
        }
        if (trackIds.size() > 500) {
            throw new IllegalArgumentException("At most 500 track ids per request, got " + trackIds.size());
        }
        return resolver.findVerified(trackIds).stream().map(PlaybackController::toResponse).toList();
    }

    private static PlaybackSourceResponse toResponse(PlaybackSource source) {
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
