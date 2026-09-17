package com.aura.recommendation.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Queue semantics for the taste-graph build. The build itself runs in worker-svc on its own Last.fm
 * key — it claims an artist here, asks the provider and posts the answer back — so the pacing and the
 * credentials live there. What stays here is how long an answer is good for and how big a seeding
 * round is.
 */
@Validated
@ConfigurationProperties(prefix = "aura.recommendations.graph-sync")
public record GraphSyncProperties(
        /** Artists pulled from catalog-svc per seeding round — cheap, no provider calls involved. */
        @DefaultValue("200") int seedPageSize,
        /** An artist's graph is asked for again after this long — tastes and the provider's data move slowly. */
        @NotNull @DefaultValue("60d") Duration refreshAfter
) {
}
