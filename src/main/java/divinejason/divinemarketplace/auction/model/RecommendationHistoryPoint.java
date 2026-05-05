package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable recommendation history point data between marketplace services, persistence stores, commands, and GUI rendering.
 */
/**
 * Compact recommendation-history checkpoint for one market key.
 *
 * This is intentionally much smaller than exact sale history and is suitable for
 * longer-term economics browsing through commands and chest GUI menus.
 */
public record RecommendationHistoryPoint(
        String marketKey,
        long recommendedUnitPrice,
        long recordedAtEpochMillis
) {
}
