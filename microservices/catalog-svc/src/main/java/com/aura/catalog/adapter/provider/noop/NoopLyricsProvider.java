package com.aura.catalog.adapter.provider.noop;

import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.port.LyricsProvider;
import com.aura.catalog.domain.port.LyricsQuery;
import com.aura.catalog.domain.port.ProviderLyrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Registered only when {@code aura.lyrics.provider=none} — the kill-switch for when no lyrics source
 * may be used (e.g. LRCLIB's community text is fine for dev but a licensed provider isn't wired yet).
 * Reports every lookup as "no lyrics"; already-cached rows stay readable.
 */
@Component
@ConditionalOnProperty(prefix = "aura.lyrics", name = "provider", havingValue = "none")
public class NoopLyricsProvider implements LyricsProvider {

    @Override
    public Provider provider() {
        return null;
    }

    @Override
    public Optional<ProviderLyrics> find(LyricsQuery query) {
        return Optional.empty();
    }
}
