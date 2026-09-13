package com.aura.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** {@code @ConfigurationProperties} beans are picked up via {@code @ConfigurationPropertiesScan} on the main class. */
@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /** Injected everywhere instead of {@code Instant.now()} so TTL logic is deterministic in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
