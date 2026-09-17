package com.aura.recommendation.config;

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

    /** Injected everywhere instead of {@code Instant.now()} so TTL logic stays deterministic. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Fans out the per-artist track reads a single feed needs: twenty candidate artists means twenty
     * calls to catalog-svc, and doing them one after another would make the feed's latency their sum.
     * Virtual threads (Java 21) fit exactly — these tasks only block on network I/O.
     */
    @Bean(destroyMethod = "shutdown")
    @HttpIo
    public ExecutorService httpIoExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
    public @interface HttpIo {
    }
}
