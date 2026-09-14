package com.aura.playback.adapter.worker;

import com.aura.playback.adapter.provider.youtube.YouTubeApiClient;
import com.aura.playback.config.VideoHintProperties;
import com.aura.playback.domain.service.CatalogServiceUnavailableException;
import com.aura.playback.domain.service.PlaybackProviderUnavailableException;
import com.aura.playback.domain.service.VideoHintService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs {@link VideoHintService#processNextPage} in a loop on one background (virtual) thread for as
 * long as the service is up. One thread on purpose — MusicBrainz allows one call per second, and
 * that call is what dominates each page. Lives here for now; it only talks to the outside through
 * ports, so it can move to its own service untouched once it deserves one.
 * Disabled with {@code playback.video-hints.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "playback.video-hints", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VideoHintWorker {

    private static final Logger log = LoggerFactory.getLogger(VideoHintWorker.class);

    private final VideoHintService service;
    private final VideoHintProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private volatile Instant startedAt;
    private volatile long pagesSinceStart;
    private volatile long matchedSinceStart;
    private volatile String lastError;

    public VideoHintWorker(VideoHintService service, VideoHintProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        thread = Thread.ofVirtual().name("video-hints").start(this::loop);
        log.info("Video hint worker started (page {}, delay {}, idle after full pass {})",
                properties.pageSize(), properties.delayBetweenPages(), properties.idleAfterFullPass());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        Thread t = thread;
        if (t != null) t.interrupt();
    }

    private void loop() {
        while (running.get()) {
            Duration pause;
            try {
                VideoHintService.PageOutcome outcome = service.processNextPage(properties.pageSize());
                pagesSinceStart++;
                matchedSinceStart += outcome.matched();
                lastError = null;
                if (outcome.endOfCatalog()) {
                    log.info("Video hint worker reached the end of the catalog (pass {}), idling {}",
                            outcome.cursor().passes(), properties.idleAfterFullPass());
                    pause = properties.idleAfterFullPass();
                } else {
                    pause = properties.delayBetweenPages();
                }
            } catch (PlaybackProviderUnavailableException | CatalogServiceUnavailableException
                     | YouTubeApiClient.QuotaExceededException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Video hint worker paused {}: {}", properties.backoffOnOutage(), lastError);
                pause = properties.backoffOnOutage();
            } catch (RuntimeException e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("Video hint worker step failed, backing off {}", properties.backoffOnOutage(), e);
                pause = properties.backoffOnOutage();
            }
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long pagesSinceStart() {
        return pagesSinceStart;
    }

    public long matchedSinceStart() {
        return matchedSinceStart;
    }

    public String lastError() {
        return lastError;
    }
}
