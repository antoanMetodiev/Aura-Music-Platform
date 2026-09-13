package com.aura.playback.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    /** Injected everywhere instead of {@code Instant.now()} so timestamps stay swappable/deterministic. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
