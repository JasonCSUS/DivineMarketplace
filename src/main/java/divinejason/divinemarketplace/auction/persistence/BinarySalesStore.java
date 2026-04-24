package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.SaleRecord;

import java.util.List;

/**
 * Primary storage for player-facing exact market sale history.
 *
 * File target:
 * - data/sales.bin
 *
 * Locked rules:
 * - stores exact recent sale history for market history and training/profile rebuilds
 * - may be FIFO-purged by size
 * - remains separate from admin audit history
 *
 * Known implementation TODO:
 * - enchant-book Sale History matching is asymmetric and should be implemented carefully:
 *   - singular enchant history includes singular sales and mixed-book sales containing that enchant
 *   - mixed-book history includes exact mixed sales plus singular component sales
 *   - mixed-book Price History stays disabled
 */
public interface BinarySalesStore {

    /**
     * Append one completed market-visible sale event.
     */
    void append(SaleRecord saleRecord);

    /**
     * Load recent sales for one market key within a time window for profile rebuilds.
     */
    List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis);

    /**
     * Load paged player-facing sale history for one market key.
     */
    List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize);

    /**
     * Purge oldest exact sale records first if the file exceeds its configured size limit.
     */
    void purgeOldestIfOverMaxSize();
}
