package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;

import java.time.YearMonth;
import java.util.List;

/**
 * Read-only history service for player-facing GUI/command lookups.
 *
 * Known complexity:
 * - enchant-book Sale History uses special matching rules
 * - Price History for mixed-enchant books is disabled
 */
public interface HistoryService {

    /**
     * Return player-facing exact sale history for one market key.
     *
     * TODO during implementation:
     * - singular enchant groups should include singular sales plus mixed-book sales containing that enchant
     * - mixed-book groups should include exact mixed sales plus singular component sales
     */
    List<SaleRecord> getSaleHistory(String marketKey, int page, int pageSize);

    /**
     * Return compact recommendation-history points for one market key and month.
     */
    List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month);

    boolean isSaleHistoryEnabled(String marketKey);

    boolean isPriceHistoryEnabled(String marketKey);
}
