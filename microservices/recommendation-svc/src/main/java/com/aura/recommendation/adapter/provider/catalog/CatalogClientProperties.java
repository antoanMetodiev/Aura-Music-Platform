package com.aura.recommendation.adapter.provider.catalog;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Where to reach catalog-svc directly (service-to-service, not through the gateway). */
@Validated
@ConfigurationProperties(prefix = "aura.catalog-client")
public record CatalogClientProperties(
        @NotBlank @DefaultValue("http://localhost:8081") String baseUrl
) {
}
