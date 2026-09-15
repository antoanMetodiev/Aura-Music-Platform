package com.aura.catalog.domain.service;

import com.aura.catalog.config.ArtistAboutProperties;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.ArtistAbout;
import com.aura.catalog.domain.model.ArtistAbout.Biography;
import com.aura.catalog.domain.model.ArtistAbout.ExternalLink;
import com.aura.catalog.domain.model.ArtistAbout.SimilarArtist;
import com.aura.catalog.domain.port.ArtistAboutStore;
import com.aura.catalog.domain.port.ArtistInfoProvider;
import com.aura.catalog.domain.port.CatalogStore;
import com.aura.catalog.domain.port.ProviderArtistInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "About" for an artist: cache → providers (in order) → cache, the same lazy-discovery shape as
 * lyrics and the catalog itself.
 *
 * <pre>
 *   read ──► artist_about ──(fresh)──────────────► return
 *               │
 *               └─(missing / stale)──► Last.fm, Discogs ──► merge ──► save ──► return
 *                                          └─(outage)──► serve what we have, else PROVIDER_UNAVAILABLE
 * </pre>
 *
 * Merge rule: the first provider with a biography supplies it; tags/similar/stats come from whoever
 * has them (Last.fm); links are the union, classified by host. "Similar" names are matched to our own
 * artists on every read, not at fetch time — the catalog keeps growing.
 */
@Service
public class ArtistAboutService {

    private static final Logger log = LoggerFactory.getLogger(ArtistAboutService.class);

    private final ArtistAboutStore store;
    private final List<ArtistInfoProvider> providers;
    private final CatalogStore catalog;
    private final ArtistAboutProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, CompletableFuture<ArtistAbout>> pending = new ConcurrentHashMap<>();

    public ArtistAboutService(ArtistAboutStore store, List<ArtistInfoProvider> providers, CatalogStore catalog,
                              ArtistAboutProperties properties, Clock clock) {
        this.store = store;
        this.providers = providers;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param language ISO 639-1 code for the biography (the UI locale); English when the providers have nothing in it
     * @throws CatalogEntityNotFoundException {@code ARTIST_NOT_FOUND} for an unknown artist
     * @throws ProviderUnavailableException   nothing cached and every provider is down
     */
    public ArtistAbout getAbout(UUID requestedId, String language) {
        Artist requested = catalog.findArtistById(requestedId).orElseThrow(() -> new CatalogEntityNotFoundException("Artist", requestedId));
        // A provider duplicate (V14) shares its canonical's About — one lookup, one cache row, per act.
        Artist artist = requested.isAlias() ? catalog.findArtistById(requested.canonicalArtistId()).orElse(requested) : requested;
        UUID artistId = artist.id();
        Optional<ArtistAbout> cached = store.find(artistId);
        if (cached.isPresent() && !isStale(cached.get())) return withCatalogMatches(cached.get());

        try {
            return withCatalogMatches(coalescedFetch(artist, language));
        } catch (ProviderUnavailableException e) {
            if (cached.isPresent()) {
                log.warn("Serving stale About for artist {} — {}", artistId, e.getMessage());
                return withCatalogMatches(cached.get());
            }
            throw e;
        }
    }

    private ArtistAbout coalescedFetch(Artist artist, String language) {
        CompletableFuture<ArtistAbout> mine = new CompletableFuture<>();
        CompletableFuture<ArtistAbout> existing = pending.putIfAbsent(artist.id(), mine);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }
        try {
            ArtistAbout about = fetch(artist, language);
            mine.complete(about);
            return about;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            pending.remove(artist.id(), mine);
        }
    }

    /** Asks every provider; one being down doesn't lose the others' answers — only all of them down is an outage. */
    private ArtistAbout fetch(Artist artist, String language) {
        Biography biography = null;
        Long listeners = null;
        Long playcount = null;
        List<String> tags = List.of();
        List<SimilarArtist> similar = List.of();
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        int answered = 0;
        ProviderUnavailableException lastOutage = null;

        for (ArtistInfoProvider provider : providers) {
            ProviderArtistInfo info;
            try {
                Optional<ProviderArtistInfo> found = provider.find(artist, language);
                answered++;
                if (found.isEmpty()) continue;
                info = found.get();
            } catch (ProviderUnavailableException e) {
                lastOutage = e;
                continue;
            }
            if (biography == null && info.hasBiography()) {
                biography = new Biography(info.biography(), provider.provider(), info.biographyUrl(), info.biographyLanguage());
            }
            if (listeners == null) listeners = info.listeners();
            if (playcount == null) playcount = info.playcount();
            if (tags.isEmpty()) tags = info.tags();
            if (similar.isEmpty()) similar = info.similar();
            urls.addAll(info.links());
        }
        if (answered == 0 && lastOutage != null) throw lastOutage;

        ArtistAbout about = new ArtistAbout(artist.id(), biography, listeners, playcount, tags, similar,
                classifyLinks(urls, biography), clock.instant());
        store.save(about);
        log.debug("About for '{}': bio={}, tags={}, similar={}, links={}", artist.name(),
                biography == null ? "none" : biography.source(), tags.size(), similar.size(), about.links().size());
        return about;
    }

    private boolean isStale(ArtistAbout about) {
        var ttl = about.isEmpty() ? properties.retryMissingAfter() : properties.refreshAfter();
        return about.fetchedAt().plus(ttl).isBefore(clock.instant());
    }

    /** Resolves "similar" names to artists we actually have, so the UI can link to them. */
    private ArtistAbout withCatalogMatches(ArtistAbout about) {
        if (about.similar().isEmpty()) return about;
        Map<String, Artist> byName = catalog.findArtistsByExactNames(about.similar().stream().map(SimilarArtist::name).toList())
                .stream().collect(Collectors.toMap(a -> a.name().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        List<SimilarArtist> matched = about.similar().stream()
                .map(s -> {
                    Artist a = byName.get(s.name().toLowerCase(Locale.ROOT));
                    return a == null || a.id().equals(about.artistId()) ? s : new SimilarArtist(s.name(), s.url(), a);
                })
                .toList();
        return new ArtistAbout(about.artistId(), about.biography(), about.listeners(), about.playcount(),
                about.tags(), matched, about.links(), about.fetchedAt());
    }

    // ── Links ──────────────────────────────────────────────────────────────────────────────

    /** Host → label the UI maps to an icon; unknown hosts are "website". One link per type, first wins; link aggregators are dropped. */
    private static final Map<String, String> HOST_TYPES = Map.ofEntries(
            Map.entry("instagram.com", "instagram"),
            Map.entry("facebook.com", "facebook"),
            Map.entry("twitter.com", "x"),
            Map.entry("x.com", "x"),
            Map.entry("tiktok.com", "tiktok"),
            Map.entry("youtube.com", "youtube"),
            Map.entry("youtu.be", "youtube"),
            Map.entry("soundcloud.com", "soundcloud"),
            Map.entry("bandcamp.com", "bandcamp"),
            Map.entry("open.spotify.com", "spotify"),
            Map.entry("music.apple.com", "apple-music"),
            Map.entry("wikipedia.org", "wikipedia"),
            Map.entry("discogs.com", "discogs"),
            Map.entry("last.fm", "lastfm"),
            Map.entry("threads.net", "threads"),
            Map.entry("vk.com", "vk"),
            Map.entry("telegram.me", "telegram"),
            Map.entry("t.me", "telegram")
    );
    private static final List<String> AGGREGATORS = List.of("linktr.ee", "linkin.bio", "beacons.ai", "lnk.to", "ffm.to", "tumblr.com", "myspace.com");

    static List<ExternalLink> classifyLinks(Iterable<String> urls, Biography biography) {
        Map<String, ExternalLink> byType = new LinkedHashMap<>();
        for (String url : urls) {
            String host = host(url);
            if (host == null || AGGREGATORS.stream().anyMatch(host::endsWith)) continue;
            String type = HOST_TYPES.entrySet().stream()
                    .filter(e -> host.equals(e.getKey()) || host.endsWith("." + e.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("website");
            byType.putIfAbsent(type, new ExternalLink(type, url));
        }
        // Credit the biography's source with a link even when the provider didn't list itself.
        if (biography != null && biography.url() != null) {
            String type = biography.source().name().toLowerCase(Locale.ROOT);
            byType.putIfAbsent(type, new ExternalLink(type, biography.url()));
        }
        return new ArrayList<>(byType.values());
    }

    private static String host(String url) {
        try {
            String host = URI.create(url.strip()).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
