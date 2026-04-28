package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecommendationHistoryStore;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Player-facing history service.
 *
 * Exact sale history and compact price history are intentionally separate:
 * - sale history uses exact SaleRecord entries
 * - price history uses one recommended-price checkpoint per day
 */
public final class DefaultHistoryService implements HistoryService {
    private final InMemorySaleHistoryIndex saleHistoryIndex;
    private final SQLiteRecommendationHistoryStore recommendationHistoryStore;

    public DefaultHistoryService(
            InMemorySaleHistoryIndex saleHistoryIndex,
            SQLiteRecommendationHistoryStore recommendationHistoryStore
    ) {
        this.saleHistoryIndex = Objects.requireNonNull(saleHistoryIndex, "saleHistoryIndex");
        this.recommendationHistoryStore = Objects.requireNonNull(recommendationHistoryStore, "recommendationHistoryStore");
    }

    @Override
    public List<SaleRecord> getSaleHistory(String marketKey, int page, int pageSize) {
        return saleHistoryIndex.getSaleHistoryForMarketKey(marketKey, page, pageSize);
    }

    @Override
    public List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month) {
        if (!isPriceHistoryEnabled(marketKey)) {
            return List.of();
        }
        return recommendationHistoryStore.getPriceHistory(marketKey, month);
    }

    @Override
    public boolean isSaleHistoryEnabled(String marketKey) {
        return true;
    }

    @Override
    public boolean isPriceHistoryEnabled(String marketKey) {
        return marketKey == null || !marketKey.startsWith("enchanted_book:mixed");
    }
}
