package com.aura.recommendation.domain.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lookup keys for an artist name. The taste provider and the metadata provider spell the same act
 * differently often enough that exact matching alone throws away a good part of the graph: the first
 * hundred artists produced "Travi$ Scott", "Lil' Wayne", "JAY-Z" and "Destiny’s Child" as names we
 * supposedly don't have — while having every one of them.
 *
 * <p>So each name yields an ordered list of keys, tried strongest first: the name as written, then a
 * loosened form. The loosening is deliberately conservative — case, accents, apostrophes (straight
 * and curly), the {@code $}/{@code S} and {@code !}/{@code i} stylizations, punctuation and repeated
 * spaces. It does not touch word order, drop words, or do anything fuzzy: matching "The Weeknd" to
 * "Weeknd" would be nice, matching "Ocean" to "Frank Ocean" would be a wrong edge in the graph
 * forever, and there is no way to have the first without risking the second.
 */
public final class ArtistNameKeys {

    private ArtistNameKeys() {
    }

    /** The name's keys, most exact first. Always at least one, never blank. */
    public static List<String> of(String name) {
        Set<String> keys = new LinkedHashSet<>();
        String exact = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!exact.isEmpty()) keys.add(exact);
        String loose = loosen(exact);
        if (!loose.isEmpty()) keys.add(loose);
        return keys.isEmpty() ? List.of("") : List.copyOf(keys);
    }

    /** The key a name should be indexed under when it is the one we already have. */
    public static String primary(String name) {
        return of(name).getFirst();
    }

    private static String loosen(String lower) {
        String unstylized = lower
                .replace('’', '\'')   // curly apostrophe
                .replace('‘', '\'')
                .replace('$', 's')          // Travi$ Scott
                .replace('!', 'i')          // P!nk
                .replace('&', ' ');
        String deAccented = Normalizer.normalize(unstylized, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return deAccented.replaceAll("[^a-z0-9]+", " ").trim();
    }
}
