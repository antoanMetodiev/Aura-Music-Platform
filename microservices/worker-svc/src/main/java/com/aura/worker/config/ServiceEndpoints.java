package com.aura.worker.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the services this worker feeds actually live. All service-to-service, never through the
 * gateway — the gateway is the browser's door, not ours.
 */
@Validated
@ConfigurationProperties(prefix = "aura.services")
public record ServiceEndpoints(
        @NotBlank @DefaultValue("http://localhost:8081") String catalogUrl,
        @NotBlank @DefaultValue("http://localhost:8082") String playbackUrl,
        @NotBlank @DefaultValue("http://localhost:8083") String recommendationUrl
) {
}
