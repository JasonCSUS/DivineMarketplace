package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SaleRecord;

/**
 * Analytics/event ingestion layer.
 *
 * Distinction:
 * - all eligible auction actions should be recorded in history/audit
 * - not all records should feed recommendation training
 */
public interface MarketAnalyticsService {

    /**
     * Optional hook for listing-created analytics/index maintenance.
     */
    void recordListingCreated(Listing listing);

    /**
     * Record a completed sale into player-facing exact sales and downstream
     * analytics/profile rebuild pipelines as appropriate.
     */
    void recordSale(SaleRecord saleRecord);

    void recordListingCancelled(Listing listing);

    void recordListingExpired(Listing listing);
}
