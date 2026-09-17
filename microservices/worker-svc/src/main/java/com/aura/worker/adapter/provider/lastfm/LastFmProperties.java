package com.aura.worker.adapter.provider.lastfm;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Last.fm (https://www.last.fm/api): free API key, ~5 requests/second per IP, and — this matters —
 * <em>non-commercial by default</em>; commercial use needs an agreement with them. The similarity
 * graph we build from their answers is a cache of their data, so it sits behind the same kind of gate
 * as TIDAL (Project-Info.md §37): {@code commercial-use-approved} stays false until that is settled,
 * and it is the flag to check before this data is used for anything beyond serving our own UI —
 * §24 explicitly forbids feeding provider-restricted content into embeddings or model training.
 *
 * <p>catalog-svc has its own Last.fm adapter for artist biographies, with its own key. Deliberately
 * separate: it calls {@code artist.getInfo} while requests are waiting, this one calls
 * {@code artist.getSimilar}/{@code getTopTags} in the background for as long as it takes. Giving each
 * its own credentials is the same reasoning that moved this worker out of recommendation-svc in the
 * first place — background work should not be able to spend the budget a user request needs.
 */
@Validated
@ConfigurationProperties(prefix = "music.providers.lastfm")
public record LastFmProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @DefaultValue("https://ws.audioscrobbler.com/2.0/") String apiBaseUrl,
        /** From https://www.last.fm/api/account/create. Blank disables the provider (and the worker). */
        @DefaultValue("") String apiKey,
        @NotBlank @DefaultValue("Aura-Music-Platform/0.1 (dev)") String userAgent,
        /** Their documented ceiling is 5/s; staying under it is cheaper than being banned. */
        @DefaultValue("3") int maxRequestsPerSecond,
        /** Not yet approved for commercial use — see the class comment. */
        @DefaultValue("false") boolean commercialUseApproved
) {
    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
