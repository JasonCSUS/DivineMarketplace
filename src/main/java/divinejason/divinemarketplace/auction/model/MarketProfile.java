package divinejason.divinemarketplace.auction.model;

/**
 * Cached economics/profile state for one market key.
 *
 * This is the compact per-item cache that the plugin reads quickly during
 * commands, recommendation lookups, and slow-burn recalculation passes.
 *
 * Raw sales/listings are scanned only when rebuilding or updating this cache.
 * The cache itself should stay minimal to avoid memory/storage bloat.
 *
 * Locked v1 fields:
 * - marketKey
 * - currentRecommendedUnitPrice
 * - averageSaleUnitPrice
 * - averageListingUnitPrice
 * - recentSaleSlope
 * - recentSaleSlopeFitness
 * - recentSaleSampleCount
 * - recentListingSampleCount
 * - lastRecalculatedAtEpochMillis
 */
public record MarketProfile(
        String marketKey,
        long currentRecommendedUnitPrice,
        long averageSaleUnitPrice,
        long averageListingUnitPrice,
        double recentSaleSlope,
        double recentSaleSlopeFitness,
        int recentSaleSampleCount,
        int recentListingSampleCount,
        long lastRecalculatedAtEpochMillis
) {
}
