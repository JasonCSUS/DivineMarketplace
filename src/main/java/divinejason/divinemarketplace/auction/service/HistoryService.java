package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;

import java.time.YearMonth;
import java.util.List;

/**
 * Read-only history service for player-facing GUI/command lookups.
 *
 * Sale history is not admin audit history. It is the compact market-facing view
 * players use to judge recent prices. Enchanted books intentionally use broader
 * matching than normal items so a single-enchant book can show relevant mixed
 * book sales, and a mixed-book page can show its component single-book sales.
 */
public interface HistoryService {

    /**
     * Returns player-facing sale history for one market key.
     *
     * Normal items use exact market-key matching. Enchanted-book groups may use
     * component matching according to InMemorySaleHistoryIndex rules.
     */
    List<SaleRecord> getSaleHistory(String marketKey, int page, int pageSize);

    /**
     * Returns compact recommended-price checkpoints for one market key/month.
     */
    List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month);

    /**
     * Returns months that actually contain price checkpoints, newest first.
     */
    List<YearMonth> getPriceHistoryMonths(String marketKey);

    boolean isSaleHistoryEnabled(String marketKey);

    boolean isPriceHistoryEnabled(String marketKey);
}
