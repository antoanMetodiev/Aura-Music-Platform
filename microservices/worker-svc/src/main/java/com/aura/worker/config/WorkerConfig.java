package com.aura.worker.config;

import com.aura.worker.adapter.client.CatalogWorkClient;
import com.aura.worker.adapter.provider.tidal.TidalProperties;
import com.aura.worker.adapter.provider.tidal.TidalRequestThrottle;
import com.aura.worker.adapter.worker.DiscographyWorker;
import com.aura.worker.domain.port.MusicMetadataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The discography sync runs as two lanes of the same worker: a bulk one that works the catalog from
 * the top, and an on-demand one that only takes artists somebody has open right now. Declared here
 * rather than as two {@code @Component} classes because they differ only in which artists they may
 * claim and how eagerly they ask.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "aura.workers.discography", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DiscographyWorker bulkDiscographyWorker(CatalogWorkClient catalog, MusicMetadataProvider metadata,
                                                   DiscographyWorkerProperties properties, TidalProperties tidal,
                                                   TidalRequestThrottle throttle) {
        return new DiscographyWorker(DiscographyWorker.Lane.BULK, properties.idleDelay(),
                catalog, metadata, properties, tidal, throttle);
    }

    @Bean
    @ConditionalOnProperty(prefix = "aura.workers.discography", name = "on-demand-lane-enabled", havingValue = "true", matchIfMissing = true)
    public DiscographyWorker onDemandDiscographyWorker(CatalogWorkClient catalog, MusicMetadataProvider metadata,
                                                       DiscographyWorkerProperties properties, TidalProperties tidal,
                                                       TidalRequestThrottle throttle) {
        return new DiscographyWorker(DiscographyWorker.Lane.ON_DEMAND, properties.onDemandPollInterval(),
                catalog, metadata, properties, tidal, throttle);
    }
}
