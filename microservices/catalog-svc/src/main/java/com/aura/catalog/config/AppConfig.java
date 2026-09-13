package com.aura.catalog.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** {@code @ConfigurationProperties} beans are picked up via {@code @ConfigurationPropertiesScan} on the main class. */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /** Injected everywhere instead of {@code Instant.now()} so TTL logic is deterministic in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Runs independent TIDAL HTTP calls concurrently instead of one-after-another (search's tracks/
     * albums/artists hydration used to serialize behind each other, so total latency was the *sum*
     * of every call). Virtual threads (Java 21) are the natural fit — these tasks just block on
     * network I/O with no other scarce resource involved, so there's no reason to cap how many run
     * at once. Shut down with the application context.
     */
    @Bean(destroyMethod = "shutdown")
    @HttpIo
    public ExecutorService httpIoExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Runs independent Postgres upserts (search persists every track/album/artist it saw —
     * Project-Info.md §14) concurrently too, but — unlike TIDAL calls — each one holds a Hikari
     * connection for its duration, and the pool only has so many
     * ({@code spring.datasource.hikari.maximum-pool-size}). Firing them all as virtual threads at
     * once made every task race for the same handful of connections: the losers queued for up to
     * Hikari's 30s {@code connectionTimeout} and a single search could look "hung." A small fixed
     * pool, sized comfortably under the connection pool, caps how many upserts truly run at once —
     * the rest wait in this executor's own (cheap, in-memory) queue instead of at the DB.
     */
    @Bean(destroyMethod = "shutdown")
    @DbWrite
    public ExecutorService dbWriteExecutor() {
        return Executors.newFixedThreadPool(6);
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface HttpIo {
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface DbWrite {
    }
}
