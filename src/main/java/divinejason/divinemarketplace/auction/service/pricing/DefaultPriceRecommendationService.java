package divinejason.divinemarketplace.auction.service.pricing;


/*
 * File role: Implements price recommendation service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.MarketProfile;
import divinejason.divinemarketplace.auction.service.category.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketPriceStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteRecommendationHistoryStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultPriceRecommendationService implements PriceRecommendationService {
    private final Logger logger;
    private final ActiveListingLookup listingLookup;
    private final MarketEventService marketEventService;
    private final MarketProfileCalculator calculator;
    private final FlattenedMarketIndexService marketIndexService;
    private final SQLiteMarketPriceStore marketPriceStore;
    private final SQLiteRecommendationHistoryStore recommendationHistoryStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;

    private final Map<String, Long> recommendedPrices = new LinkedHashMap<>();

    public DefaultPriceRecommendationService(
            JavaPlugin plugin,
            FlattenedMarketIndexService marketIndexService,
            ActiveListingLookup listingLookup,
            MarketEventService marketEventService,
            MarketProfileCalculator calculator,
            SQLiteMarketPriceStore marketPriceStore,
            SQLiteRecommendationHistoryStore recommendationHistoryStore,
            SQLiteWriteBehindQueue writeBehindQueue
    ) {
        this.logger = plugin.getLogger();
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        this.listingLookup = Objects.requireNonNull(listingLookup, "listingLookup");
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.marketPriceStore = Objects.requireNonNull(marketPriceStore, "marketPriceStore");
        this.recommendationHistoryStore = Objects.requireNonNull(recommendationHistoryStore, "recommendationHistoryStore");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
    }

    /** Loads recommended prices from SQLite and fills any missing market keys with zero. */
    public synchronized void loadFromStorage() {
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
            SQLiteWriteBatch.Builder batch = SQLiteWriteBatch.builder("seed missing market prices");
            for (Map.Entry<String, Long> entry : recommendedPrices.entrySet()) {
                batch.add(marketPriceStore.putMutation(entry.getKey(), entry.getValue()));
            }
            writeBehindQueue.enqueue(batch.build());
        }
    }

    @Override
    public synchronized void reload() {
        loadFromStorage();
    }

    @Override
    public synchronized long getRecommendedUnitPrice(String marketKey) {
        return recommendedPrices.getOrDefault(marketKey, 0L);
    }

    @Override
    public synchronized void forceRecalculate(String marketKey) {
        MarketProfile profile = calculateProfile(marketKey);
        long price = profile.currentRecommendedUnitPrice();
        long now = profile.lastRecalculatedAtEpochMillis();

        recommendedPrices.put(marketKey, price);
        recommendationHistoryStore.upsertDailyPointInMemory(marketKey, price, now);

        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("price recalc " + marketKey)
                .add(marketPriceStore.putMutation(marketKey, price))
                .add(recommendationHistoryStore.upsertDailyPointMutation(marketKey, price, now))
                .build());
    }

    @Override
    public synchronized int forceRecalculateAll(Iterable<String> marketKeys) {
        int count = 0;
        List<MarketProfile> recalculatedProfiles = new ArrayList<>();
        Map<String, List<Listing>> activeListingsByMarketKey = listingLookup.getAllActiveListings().stream()
                .collect(Collectors.groupingBy(Listing::marketKey, LinkedHashMap::new, Collectors.toList()));

        for (String marketKey : marketKeys) {
            try {
                MarketProfile profile = calculateProfile(marketKey, activeListingsByMarketKey.getOrDefault(marketKey, List.of()));
                recommendedPrices.put(marketKey, profile.currentRecommendedUnitPrice());
                recalculatedProfiles.add(profile);
                count++;
            } catch (RuntimeException exception) {
                logger.warning("Failed to recalculate price for " + marketKey + ": " + exception.getMessage());
            }
        }

        SQLiteWriteBatch.Builder batch = SQLiteWriteBatch.builder("price recalc all " + count + " keys");
        for (Map.Entry<String, Long> entry : recommendedPrices.entrySet()) {
            batch.add(marketPriceStore.putMutation(entry.getKey(), entry.getValue()));
        }
        for (MarketProfile profile : recalculatedProfiles) {
            recommendationHistoryStore.upsertDailyPointInMemory(
                    profile.marketKey(),
                    profile.currentRecommendedUnitPrice(),
                    profile.lastRecalculatedAtEpochMillis()
            );
            batch.add(recommendationHistoryStore.upsertDailyPointMutation(
                    profile.marketKey(),
                    profile.currentRecommendedUnitPrice(),
                    profile.lastRecalculatedAtEpochMillis()
            ));
        }
        writeBehindQueue.enqueue(batch.build());

        return count;
    }

    @Override
    public synchronized void setManualRecommendedUnitPrice(String marketKey, long unitPrice) {
        long clamped = Math.max(0L, unitPrice);
        recommendedPrices.put(marketKey, clamped);
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("manual price set " + marketKey)
                .add(marketPriceStore.putMutation(marketKey, clamped))
                .build());
    }

    private MarketProfile calculateProfile(String marketKey) {
        return calculateProfile(marketKey, listingLookup.getActiveListingsForMarketKey(marketKey));
    }

    private MarketProfile calculateProfile(String marketKey, List<Listing> activeListings) {
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

        var recentSales = marketEventService.getRecentSalesForMarketKey(
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

    public interface ActiveListingLookup {
        List<Listing> getActiveListingsForMarketKey(String marketKey);

        List<Listing> getAllActiveListings();
    }
}
