package com.aura.catalog.adapter.provider.tidal;

/**
 * Marks the current thread — and every virtual thread it spawns for hydration — as doing
 * background work (the discography worker), so {@link TidalRequestThrottle} lets interactive
 * calls go first and {@link TidalApiClient} keeps the worker's failures out of the circuit breaker
 * that guards user-facing requests. Inheritable so the {@code httpIoExecutor}'s virtual threads
 * (which inherit thread-locals) carry it.
 */
public final class TidalCallPriority {

    private static final InheritableThreadLocal<Boolean> BACKGROUND = new InheritableThreadLocal<>();

    private TidalCallPriority() {
    }

    public static boolean isBackground() {
        return Boolean.TRUE.equals(BACKGROUND.get());
    }

    public static <T> T runAsBackground(java.util.function.Supplier<T> work) {
        Boolean previous = BACKGROUND.get();
        BACKGROUND.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous == null) BACKGROUND.remove();
            else BACKGROUND.set(previous);
        }
    }
}
