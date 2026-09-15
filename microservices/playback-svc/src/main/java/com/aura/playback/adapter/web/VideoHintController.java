package com.aura.playback.adapter.web;

import com.aura.playback.adapter.worker.VideoHintWorker;
import com.aura.playback.config.VideoHintProperties;
import com.aura.playback.domain.port.VideoHintStore;
import com.aura.playback.domain.service.VideoHintService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Observability for the background video-hint discovery. */
@RestController
@RequestMapping("/api/v1/playback/discovery")
public class VideoHintController {

    public record WorkerStatus(boolean enabled, boolean running, Instant startedAt, long pagesSinceStart,
                               long matchedSinceStart, long reusedSinceStart, String lastError) {
    }

    public record StatusResponse(WorkerStatus worker, VideoHintStore.Stats hints, VideoHintStore.Cursor cursor,
                                 String lastMatched, List<VideoHintStore.Hint> recent) {
    }

    private final VideoHintService service;
    private final VideoHintProperties properties;
    private final ObjectProvider<VideoHintWorker> worker;

    public VideoHintController(VideoHintService service, VideoHintProperties properties, ObjectProvider<VideoHintWorker> worker) {
        this.service = service;
        this.properties = properties;
        this.worker = worker;
    }

    @GetMapping("/status")
    public StatusResponse status(@RequestParam(value = "recent", required = false) Integer recent) {
        VideoHintWorker w = worker.getIfAvailable();
        WorkerStatus status = new WorkerStatus(properties.enabled(), w != null && w.isRunning(),
                w == null ? null : w.startedAt(), w == null ? 0 : w.pagesSinceStart(),
                w == null ? 0 : w.matchedSinceStart(), w == null ? 0 : w.reusedSinceStart(), w == null ? null : w.lastError());
        int limit = recent == null ? 20 : Math.min(Math.max(recent, 1), 200);
        return new StatusResponse(status, service.stats(), service.cursor().orElse(null),
                service.lastMatched().orElse(null), service.recent(limit));
    }

    /** Runs exactly one page now, on the request thread — for checking the pipeline without waiting for the worker. */
    @GetMapping("/process-next")
    public VideoHintService.PageOutcome processNext(@RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return service.processNextPage(pageSize == null ? properties.pageSize() : Math.min(Math.max(pageSize, 1), 100));
    }
}
