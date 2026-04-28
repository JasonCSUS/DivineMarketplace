package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.MarketProfile;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMarketPriceStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecommendationHistoryStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class DefaultPriceRecommendationService implements PriceRecommendationService {
    private final Logger logger;
    private final BinaryListingLookup listingLookup;
    private final InMemorySaleHistoryIndex saleHistoryIndex;
    private final MarketProfileCalculator calculator;
    private final FlattenedMarketIndexService marketIndexService;
    private final SQLiteMarketPriceStore marketPriceStore;
    private final SQLiteRecommendationHistoryStore recommendationHistoryStore;

    private final Map<String, Long> recommendedPrices = new LinkedHashMap<>();

    public DefaultPriceRecommendationService(
            JavaPlugin plugin,
            FlattenedMarketIndexService marketIndexService,
            BinaryListingLookup listingLookup,
            InMemorySaleHistoryIndex saleHistoryIndex,
            MarketProfileCalculator calculator,
            SQLiteMarketPriceStore marketPriceStore,
            SQLiteRecommendationHistoryStore recommendationHistoryStore
    ) {
        this.logger = plugin.getLogger();
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        this.listingLookup = Objects.requireNonNull(listingLookup, "listingLookup");
        this.saleHistoryIndex = Objects.requireNonNull(saleHistoryIndex, "saleHistoryIndex");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.marketPriceStore = Objects.requireNonNull(marketPriceStore, "marketPriceStore");
        this.recommendationHistoryStore = Objects.requireNonNull(recommendationHistoryStore, "recommendationHistoryStore");
        reload();
    }

    @Override
    public synchronized void reload() {
        recommendedPrices.clear();
        recommendedPrices.putAll(marketPriceStore.loadAll());

        boolean changed = false;
        for (String marketKey : marketIndexService.getKnownMarketKeys()) {
            if (!recommendedPrices.containsKey(marketKey)) {
                recommendedPrices.put(marketKey, 0L);
                changed = true;
            }
        }

        if (changed) {
            marketPriceStore.replaceAll(recommendedPrices);
        }
    }

    @Override
    public synchronized long getRecommendedUnitPrice(String marketKey) {
        return recommendedPrices.getOrDefault(marketKey, 0L);
    }

    @Override
    public synchronized void forceRecalculate(String marketKey) {
        MarketProfile profile = calculateProfile(marketKey);
        recommendedPrices.put(marketKey, profile.currentRecommendedUnitPrice());
        marketPriceStore.put(marketKey, profile.currentRecommendedUnitPrice());
        recommendationHistoryStore.upsertDailyPoint(
                marketKey,
                profile.currentRecommendedUnitPrice(),
                profile.lastRecalculatedAtEpochMillis()
        );
    }

    @Override
    public synchronized int forceRecalculateAll(Iterable<String> marketKeys) {
        int count = 0;
        List<MarketProfile> recalculatedProfiles = new ArrayList<>();

        for (String marketKey : marketKeys) {
            try {
                MarketProfile profile = calculateProfile(marketKey);
                recommendedPrices.put(marketKey, profile.currentRecommendedUnitPrice());
                recalculatedProfiles.add(profile);
                count++;
            } catch (RuntimeException exception) {
                logger.warning("Failed to recalculate price for " + marketKey + ": " + exception.getMessage());
            }
        }

        marketPriceStore.replaceAll(recommendedPrices);

        for (MarketProfile profile : recalculatedProfiles) {
            recommendationHistoryStore.upsertDailyPoint(
                    profile.marketKey(),
                    profile.currentRecommendedUnitPrice(),
                    profile.lastRecalculatedAtEpochMillis()
            );
        }

        return count;
    }

    @Override
    public synchronized void setManualRecommendedUnitPrice(String marketKey, long unitPrice) {
        long clamped = Math.max(0L, unitPrice);
        recommendedPrices.put(marketKey, clamped);
        marketPriceStore.put(marketKey, clamped);
    }

    private MarketProfile calculateProfile(String marketKey) {
        MarketProfile oldProfile = new MarketProfile(
                marketKey,
                getRecommendedUnitPrice(marketKey),
                0L,
                0L,
                0.0,
                0.0,
                0,
                0,
                0L
        );

        List<Listing> activeListings = listingLookup.getActiveListingsForMarketKey(marketKey);
        var recentSales = saleHistoryIndex.getRecentSalesForMarketKey(
                marketKey,
                divinejason.divinemarketplace.config.ConfigService.get().marketSaleLookbackMillis()
        );

        return calculator.calculate(
                marketKey,
                oldProfile,
                recentSales,
                activeListings,
                System.currentTimeMillis()
        );
    }

    public interface BinaryListingLookup {
        List<Listing> getActiveListingsForMarketKey(String marketKey);
    }
}
