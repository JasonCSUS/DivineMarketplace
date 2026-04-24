package divinejason.divinemarketplace.auction.repository;

import divinejason.divinemarketplace.auction.model.SaleRecord;

import java.util.List;

/**
 * Logical sale-history repository abstraction.
 */
public interface SaleRepository {
    void append(SaleRecord saleRecord);

    List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis);

    List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize);
}
