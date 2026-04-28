package divinejason.divinemarketplace.auction.service;

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
        salesStore.purgeOldestIfOverMaxSize();
        historyIndex.recordSale(saleRecord);
    }

    @Override
    public void recordListingCancelled(Listing listing) {
    }

    @Override
    public void recordListingExpired(Listing listing) {
    }
}
