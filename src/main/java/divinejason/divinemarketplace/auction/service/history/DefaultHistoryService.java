package divinejason.divinemarketplace.auction.service.history;

/*
 * Layer : service
 * Owns  : history behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Implements history service behavior reading from the canonical market event service and recommendation history store.
 */
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteRecommendationHistoryStore;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Player-facing history service.
 *
 * Exact sale history is now a projection of BUY events from MarketEventService.
 * Price history continues to use one recommended-price checkpoint per day.
 */
public final class DefaultHistoryService implements HistoryService {
    private final MarketEventService marketEventService;
    private final SQLiteRecommendationHistoryStore recommendationHistoryStore;

    public DefaultHistoryService(
            MarketEventService marketEventService,
            SQLiteRecommendationHistoryStore recommendationHistoryStore
    ) {
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
        this.recommendationHistoryStore = Objects.requireNonNull(recommendationHistoryStore, "recommendationHistoryStore");
    }

    @Override
    public List<SaleRecord> getSaleHistory(String marketKey, int page, int pageSize) {
        return marketEventService.getSaleHistoryForMarketKey(marketKey, page, pageSize);
    }

    @Override
    public List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month) {
        if (!isPriceHistoryEnabled(marketKey)) {
            return List.of();
        }
        return recommendationHistoryStore.getPriceHistory(marketKey, month);
    }

    @Override
    public List<YearMonth> getPriceHistoryMonths(String marketKey) {
        if (!isPriceHistoryEnabled(marketKey)) {
            return List.of();
        }
        return recommendationHistoryStore.getMonthsWithData(marketKey);
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
