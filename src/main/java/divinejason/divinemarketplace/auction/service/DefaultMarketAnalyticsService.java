package divinejason.divinemarketplace.auction.service;


/*
 * File role: Implements market analytics service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteSalesStore;

import java.util.Objects;

public final class DefaultMarketAnalyticsService implements MarketAnalyticsService {
    private final SQLiteSalesStore salesStore;
    private final InMemorySaleHistoryIndex historyIndex;

    public DefaultMarketAnalyticsService(SQLiteSalesStore salesStore, InMemorySaleHistoryIndex historyIndex) {
        this.salesStore = Objects.requireNonNull(salesStore, "salesStore");
        this.historyIndex = Objects.requireNonNull(historyIndex, "historyIndex");
    }

    @Override
    public void recordListingCreated(Listing listing) {
    }

    @Override
    public void recordSale(SaleRecord saleRecord) {
        salesStore.append(saleRecord);
        int purged = salesStore.purgeOldestIfOverMaxSize();
        if (purged > 0) {
            historyIndex.reload();
        } else {
            historyIndex.recordSale(saleRecord);
        }
    }

    @Override
    public void recordListingCancelled(Listing listing) {
    }

    @Override
    public void recordListingExpired(Listing listing) {
    }
}
