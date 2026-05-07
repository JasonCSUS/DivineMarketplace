package divinejason.divinemarketplace.util;

/*
 * Layer : util
 * Owns  : lightweight nanosecond stopwatch; reads debug.performanceTimings gate from config
 */

import divinejason.divinemarketplace.config.ConfigService;

/**
 * Lightweight stopwatch for optional performance logging.
 *
 * <p>Call {@link #enabled()} first; if it returns {@code false}, skip the
 * {@link #start()} call entirely so there is no allocation cost in hot paths.</p>
 */
public final class PerfTimer {
    private final long startNanos = System.nanoTime();

    private PerfTimer() {}

    /** Returns {@code true} when {@code debug.performanceTimings} is enabled. */
    public static boolean enabled() {
        try {
            return ConfigService.get().performanceTimings();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** Starts a new timer. Only call after confirming {@link #enabled()}. */
    public static PerfTimer start() {
        return new PerfTimer();
    }

    /** Milliseconds elapsed since {@link #start()} was called. */
    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
