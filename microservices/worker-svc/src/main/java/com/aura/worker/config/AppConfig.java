package com.aura.worker.config;

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

    /** Injected instead of {@code Instant.now()} so timestamps stay swappable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Hydrating one artist's tracklist means a run of independent batch fetches; done one after
     * another their latencies add up, and the provider throttle — not the thread count — is what
     * actually paces us. Virtual threads (Java 21) suit tasks that only block on network I/O.
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
