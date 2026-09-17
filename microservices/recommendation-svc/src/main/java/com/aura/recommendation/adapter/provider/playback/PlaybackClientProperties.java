package com.aura.recommendation.adapter.provider.playback;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Where to reach playback-svc directly (service-to-service, not through the gateway). */
@Validated
@ConfigurationProperties(prefix = "aura.playback-client")
public record PlaybackClientProperties(
        @NotBlank @DefaultValue("http://localhost:8082") String baseUrl
) {
}
