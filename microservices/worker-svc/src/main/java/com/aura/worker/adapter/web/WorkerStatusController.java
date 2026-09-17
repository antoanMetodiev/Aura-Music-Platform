package com.aura.worker.adapter.web;

import com.aura.worker.adapter.provider.lastfm.LastFmProperties;
import com.aura.worker.adapter.provider.tidal.TidalProperties;
import com.aura.worker.adapter.worker.DiscographyWorker;
import com.aura.worker.adapter.worker.GraphWorker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * What this service is doing right now. It reports only its own side — how fast it is going, what the
 * providers are saying, what it last handed over. How much of the catalog or the graph exists is the
 * owning service's question, answered by its own {@code /discovery/status}.
 */
@RestController
@RequestMapping("/api/v1/workers")
public class WorkerStatusController {

    public record WorkerStatus(String name, String provider, boolean enabled, boolean running,
                               boolean credentialsConfigured, Instant startedAt, double currentRequestsPerSecond,
                               String inProgress, long unitsSinceStart, long producedSinceStart, Object lastOutcome) {
    }

    private final ObjectProvider<DiscographyWorker> discography;
    private final ObjectProvider<GraphWorker> graph;
    private final TidalProperties tidal;
    private final LastFmProperties lastfm;

    public WorkerStatusController(ObjectProvider<DiscographyWorker> discography, ObjectProvider<GraphWorker> graph,
                                  TidalProperties tidal, LastFmProperties lastfm) {
        this.discography = discography;
        this.graph = graph;
        this.tidal = tidal;
        this.lastfm = lastfm;
    }

    @GetMapping("/status")
    public List<WorkerStatus> status() {
        return List.of(discographyStatus(), graphStatus());
    }

    private WorkerStatus discographyStatus() {
        DiscographyWorker w = discography.getIfAvailable();
        return new WorkerStatus("discography", "TIDAL", w != null, w != null && w.isRunning(), tidal.configured(),
                w == null ? null : w.startedAt(),
                w == null ? 0 : w.currentRequestsPerSecond(),
                w == null ? null : w.inProgress(),
                w == null ? 0 : w.artistsSinceStart(),
                w == null ? 0 : w.tracksSinceStart(),
                w == null ? null : w.lastOutcome());
    }

    private WorkerStatus graphStatus() {
        GraphWorker w = graph.getIfAvailable();
        return new WorkerStatus("taste-graph", "LASTFM", w != null, w != null && w.isRunning(), lastfm.configured(),
                w == null ? null : w.startedAt(),
                w == null ? 0 : w.currentRequestsPerSecond(),
                w == null ? null : w.inProgress(),
                w == null ? 0 : w.artistsSinceStart(),
                w == null ? 0 : w.edgesSinceStart(),
                w == null ? null : w.lastOutcome());
    }
}
