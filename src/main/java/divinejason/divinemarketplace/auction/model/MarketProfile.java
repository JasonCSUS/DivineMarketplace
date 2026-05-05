package divinejason.divinemarketplace.auction.model;

/**
 * In-memory market calculation result for one market key.
 *
 * Current role:
 * - temporary output from MarketProfileCalculator during recalculation
 * - not a persisted runtime cache file
 * - used to keep recommendation math readable even though recommended prices are
 *   ultimately written to SQLite market_prices storage
 *
 * Important:
 * - this class is intentionally kept even though legacy market profile binary cache has been removed
 * - storage/repository classes that used to persist it are now legacy deletion candidates
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
