package divinejason.divinemarketplace.auction.service;

/**
 * Read/write façade around current recommendation data.
 *
 * MarketProfileCalculator and MarketRecalculationService contain the real
 * recommendation math and scheduling. This service is the higher-level access
 * layer used by menus, commands, and admin tools.
 */
public interface PriceRecommendationService {

    /**
     * Get the current recommended unit price for a market key.
     */
    long getRecommendedUnitPrice(String marketKey);

    /**
     * Force immediate recalculation of one market key.
     *
     * The recalculation service should update lastRecalculatedAtEpochMillis so the
     * normal global pass skips that item until its per-item cooldown has elapsed.
     */
    void forceRecalculate(String marketKey);

    /**
     * Admin override of the current recommendation.
     *
     * PSEUDOCODE:
     * - load MarketProfile
     * - replace currentRecommendedUnitPrice
     * - update lastRecalculatedAtEpochMillis to now
     * - save profile
     */
    void setManualRecommendedUnitPrice(String marketKey, long unitPrice);
}
