package com.aura.catalog.adapter.provider.lrclib;

import com.aura.catalog.adapter.provider.lrclib.dto.LrclibLyricsResponse;
import com.aura.catalog.domain.model.Lyrics;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.LyricsProvider;
import com.aura.catalog.domain.port.LyricsQuery;
import com.aura.catalog.domain.port.ProviderLyrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link LyricsProvider} over LRCLIB (todo.md §2.2). Lookup order, cheapest and most exact first:
 * <ol>
 *   <li>{@code /get} with artist + title + album + duration;</li>
 *   <li>{@code /get} without the album (compilations vs. singles often disagree on it);</li>
 *   <li>both again with Cyrillic artist/title transliterated — LRCLIB contributors frequently file
 *       Bulgarian songs under Latin spellings;</li>
 *   <li>{@code /search} by artist + title, first hit whose duration is within tolerance.</li>
 * </ol>
 * Active by default; {@code aura.lyrics.provider=none} swaps in {@link com.aura.catalog.adapter.provider.noop.NoopLyricsProvider}.
 */
@Component
@ConditionalOnProperty(prefix = "aura.lyrics", name = "provider", havingValue = "lrclib", matchIfMissing = true)
public class LrclibLyricsProvider implements LyricsProvider {

    private static final Logger log = LoggerFactory.getLogger(LrclibLyricsProvider.class);

    private final LrclibApiClient client;
    private final LrclibProperties properties;

    public LrclibLyricsProvider(LrclibApiClient client, LrclibProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Provider provider() {
        return Provider.LRCLIB;
    }

    @Override
    public Optional<ProviderLyrics> find(LyricsQuery query) {
        long seconds = Math.round(query.durationMs() / 1000.0);

        // Each (artist, title) spelling is tried with and without the album; the transliterated
        // pair is added only when it actually differs, so Latin-only titles cost two calls, not four.
        Set<Names> spellings = new LinkedHashSet<>();
        spellings.add(new Names(query.artistName(), query.title()));
        spellings.add(new Names(transliterate(query.artistName()), transliterate(query.title())));

        for (Names names : spellings) {
            Optional<LrclibLyricsResponse> hit = client.get(names.artist(), names.title(), query.albumTitle(), seconds)
                    .filter(LrclibLyricsResponse::hasAnyText);
            if (hit.isEmpty() && query.albumTitle() != null) {
                hit = client.get(names.artist(), names.title(), null, seconds).filter(LrclibLyricsResponse::hasAnyText);
            }
            if (hit.isPresent()) return hit.map(LrclibLyricsProvider::toDomain);
        }

        for (Names names : spellings) {
            Optional<LrclibLyricsResponse> hit = client.search(names.artist(), names.title()).stream()
                    .filter(LrclibLyricsResponse::hasAnyText)
                    .filter(r -> withinTolerance(r, seconds))
                    .findFirst();
            if (hit.isPresent()) {
                log.debug("LRCLIB fuzzy match for '{} - {}' -> #{} '{} - {}'", query.artistName(), query.title(),
                        hit.get().id(), hit.get().artistName(), hit.get().trackName());
                return hit.map(LrclibLyricsProvider::toDomain);
            }
        }
        return Optional.empty();
    }

    private boolean withinTolerance(LrclibLyricsResponse r, long ourSeconds) {
        if (r.duration() == null) return false;
        return Math.abs(Math.round(r.duration()) - ourSeconds) <= properties.searchDurationToleranceSeconds();
    }

    private static ProviderLyrics toDomain(LrclibLyricsResponse r) {
        List<Lyrics.SyncedLine> synced = LrcParser.parse(r.syncedLyrics());
        String plain = r.plainLyrics() == null || r.plainLyrics().isBlank() ? null : r.plainLyrics().strip();
        return new ProviderLyrics(r.instrumental(), synced.isEmpty() ? null : synced, plain);
    }

    private record Names(String artist, String title) {
    }

    // ── Transliteration (copied from playback-svc TrackMatcher — no shared domain between services) ──

    /** Bulgarian streamlined system, plus the handful of Russian/Ukrainian letters that show up in titles. */
    private static final Map<Character, String> CYRILLIC_TO_LATIN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"),
            Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"), Map.entry('у', "u"),
            Map.entry('ф', "f"), Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"), Map.entry('ш', "sh"),
            Map.entry('щ', "sht"), Map.entry('ъ', "a"), Map.entry('ь', "y"), Map.entry('ю', "yu"), Map.entry('я', "ya"),
            Map.entry('ё', "yo"), Map.entry('э', "e"), Map.entry('ы', "y"), Map.entry('є', "ye"), Map.entry('і', "i"),
            Map.entry('ї', "yi"), Map.entry('ґ', "g")
    );

    /** Letter-by-letter; case is preserved by capitalising the mapped form when the source was upper-case. */
    static String transliterate(String value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String mapped = CYRILLIC_TO_LATIN.get(Character.toLowerCase(c));
            if (mapped == null) {
                out.append(c);
            } else if (Character.isUpperCase(c)) {
                out.append(mapped.substring(0, 1).toUpperCase(Locale.ROOT)).append(mapped.substring(1));
            } else {
                out.append(mapped);
            }
        }
        return out.toString();
    }
}
