package com.aura.catalog.adapter.provider.lrclib;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** LRCLIB (https://lrclib.net) — free, keyless; asks only for a descriptive User-Agent. */
@Validated
@ConfigurationProperties(prefix = "aura.lyrics.lrclib")
public record LrclibProperties(
        @NotBlank @DefaultValue("https://lrclib.net/api") String baseUrl,
        @NotBlank @DefaultValue("Aura-Music-Platform/0.1 (dev)") String userAgent,
        /** A fuzzy {@code /search} hit is accepted only when its duration is within this many seconds of ours. */
        @DefaultValue("5") int searchDurationToleranceSeconds
) {
}
