package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates sort mode values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Locked UI sort modes for listing-heavy screens.
 *
 * Cycle order for v1 menu planning:
 * - NEWEST_FIRST
 * - OLDEST_FIRST
 * - LOWEST_TO_HIGHEST
 * - HIGHEST_TO_LOWEST
 */
public enum SortMode {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LOWEST_TO_HIGHEST,
    HIGHEST_TO_LOWEST;

    public SortMode next() {
        return switch (this) {
            case NEWEST_FIRST -> OLDEST_FIRST;
            case OLDEST_FIRST -> LOWEST_TO_HIGHEST;
            case LOWEST_TO_HIGHEST -> HIGHEST_TO_LOWEST;
            case HIGHEST_TO_LOWEST -> NEWEST_FIRST;
        };
    }
}
