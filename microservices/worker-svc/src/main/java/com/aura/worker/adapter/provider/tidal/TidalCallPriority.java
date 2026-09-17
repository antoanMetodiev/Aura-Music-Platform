package com.aura.worker.adapter.provider.tidal;

import java.util.function.Supplier;

/**
 * Marks the current thread — and every virtual thread it spawns for hydration — as serving somebody
 * who is waiting, so {@link TidalRequestThrottle} lets it past the bulk walk.
 *
 * <p>Both lanes of the discography worker share one throttle, because they share one TIDAL key and
 * therefore one rate-limit budget. Without a priority between them the on-demand lane queues behind
 * the bulk lane's dozens of back-to-back calls: measured at 101 seconds for a fetch that costs six
 * calls, which defeats the point of having the lane at all.
 *
 * <p>Inheritable so the {@code httpIoExecutor}'s virtual threads (which inherit thread-locals at
 * creation) carry it into the parallel hydration batches.
 */
public final class TidalCallPriority {

    private static final InheritableThreadLocal<Boolean> ON_DEMAND = new InheritableThreadLocal<>();

    private TidalCallPriority() {
    }

    public static boolean isOnDemand() {
        return Boolean.TRUE.equals(ON_DEMAND.get());
    }

    /** Runs {@code work} with every TIDAL call it makes marked as on-demand. */
    public static <T> T runAsOnDemand(Supplier<T> work) {
        Boolean previous = ON_DEMAND.get();
        ON_DEMAND.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous == null) ON_DEMAND.remove();
            else ON_DEMAND.set(previous);
        }
    }
}
