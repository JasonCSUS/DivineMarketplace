package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.BinarySalesStore;

import java.util.Objects;

/**
 * Minimal concrete market analytics implementation.
 *
 * Current role:
 * - persist eligible SaleRecord entries into BinarySalesStore
 * - keep listing lifecycle hooks wired and harmless until richer analytics exist
 */
public final class DefaultMarketAnalyticsService implements MarketAnalyticsService {
    private final BinarySalesStore salesStore;

    public DefaultMarketAnalyticsService(BinarySalesStore salesStore) {
        this.salesStore = Objects.requireNonNull(salesStore, "salesStore");
    }

    @Override
    public void recordListingCreated(Listing listing) {
        // richer analytics hooks can be added later
    }

    @Override
    public void recordSale(SaleRecord saleRecord) {
        salesStore.append(saleRecord);
        salesStore.purgeOldestIfOverMaxSize();
    }

    @Override
    public void recordListingCancelled(Listing listing) {
        // richer analytics hooks can be added later
    }

    @Override
    public void recordListingExpired(Listing listing) {
        // richer analytics hooks can be added later
    }
}
