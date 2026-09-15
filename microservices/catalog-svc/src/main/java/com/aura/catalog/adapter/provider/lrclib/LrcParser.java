package com.aura.catalog.adapter.provider.lrclib;

import com.aura.catalog.domain.model.Lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC text ({@code [mm:ss.xx] line}) into ordered {@link Lyrics.SyncedLine}s. A line carrying
 * several timestamps ({@code [00:10.00][00:40.00]chorus}) is emitted once per timestamp; empty lines
 * and {@code ♪} markers are kept as instrumental pauses so the player can blank the highlight.
 * Metadata tags ({@code [ar:…]}, {@code [ti:…]}) and untimed lines are skipped.
 */
final class LrcParser {

    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LrcParser() {
    }

    static List<Lyrics.SyncedLine> parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return List.of();
        List<Lyrics.SyncedLine> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIMESTAMP.matcher(raw);
            List<Long> times = new ArrayList<>(1);
            int textStart = 0;
            // Timestamps must be contiguous from the start of the line; anything after them is the text.
            while (m.find() && m.start() == textStart) {
                times.add(toMillis(m.group(1), m.group(2), m.group(3)));
                textStart = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(textStart).strip();
            for (long t : times) lines.add(new Lyrics.SyncedLine(t, text));
        }
        lines.sort(Comparator.comparingLong(Lyrics.SyncedLine::timeMs));
        return List.copyOf(lines);
    }

    /** Fraction is centiseconds by convention, but 1- and 3-digit variants show up too. */
    private static long toMillis(String minutes, String seconds, String fraction) {
        long ms = (Long.parseLong(minutes) * 60 + Long.parseLong(seconds)) * 1000;
        if (fraction != null) {
            ms += switch (fraction.length()) {
                case 1 -> Long.parseLong(fraction) * 100;
                case 2 -> Long.parseLong(fraction) * 10;
                default -> Long.parseLong(fraction);
            };
        }
        return ms;
    }
}
