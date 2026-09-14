package com.aura.playback.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "playback.resolver")
public record ResolverProperties(
        /** A stored non-verified outcome (candidate / nothing found) is re-resolved once it's older than this. */
        @NotNull @DefaultValue("7d") Duration retryUnverifiedAfter
) {
}
