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
import java.util.ArrayList;
import java.util.List;

/**
 * What this service is doing right now. It reports only its own side — how fast it is going, what the
 * providers are saying, what it last handed over. How much of the catalog or the graph exists is the
 * owning service's question, answered by its own {@code /discovery/status}.
 */
@RestController
@RequestMapping("/api/v1/workers")
public class WorkerStatusController {

    public record WorkerStatus(String name, String provider, boolean running, boolean credentialsConfigured,
                               Instant startedAt, double currentRequestsPerSecond, String inProgress,
                               long unitsSinceStart, long producedSinceStart, Object lastOutcome) {
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
        List<WorkerStatus> all = new ArrayList<>();
        DiscographyWorker d = discography.getIfAvailable();
        all.add(new WorkerStatus("discography", "TIDAL", d != null && d.isRunning(), tidal.configured(),
                d == null ? null : d.startedAt(),
                d == null ? 0 : d.currentRequestsPerSecond(),
                d == null ? null : d.inProgress(),
                d == null ? 0 : d.artistsSinceStart(),
                d == null ? 0 : d.tracksSinceStart(),
                d == null ? null : d.lastOutcome()));
        GraphWorker g = graph.getIfAvailable();
        all.add(new WorkerStatus("taste-graph", "LASTFM", g != null && g.isRunning(), lastfm.configured(),
                g == null ? null : g.startedAt(),
                g == null ? 0 : g.currentRequestsPerSecond(),
                g == null ? null : g.inProgress(),
                g == null ? 0 : g.artistsSinceStart(),
                g == null ? 0 : g.edgesSinceStart(),
                g == null ? null : g.lastOutcome()));
        return all;
    }
}
