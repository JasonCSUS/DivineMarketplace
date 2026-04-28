package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.setup.MarketRuntimeStateStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class MarketRecalculationService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final FlattenedMarketIndexService marketIndexService;
    private final PriceRecommendationService priceRecommendationService;
    private final MarketRuntimeStateStore runtimeStateStore;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MarketRecalculationService(
            JavaPlugin plugin,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            MarketRuntimeStateStore runtimeStateStore
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        this.priceRecommendationService = Objects.requireNonNull(priceRecommendationService, "priceRecommendationService");
        this.runtimeStateStore = Objects.requireNonNull(runtimeStateStore, "runtimeStateStore");
    }

    public void scheduleStartupAndDailyChecks() {
        checkAndScheduleDailyRecalculation();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndScheduleDailyRecalculation, 20L * 60L * 60L, 20L * 60L * 60L);
    }

    public void checkAndScheduleDailyRecalculation() {
        LocalDate today = LocalDate.now();
        LocalDate lastDate = runtimeStateStore.getLastGlobalRecalcDate();
        if (lastDate == null || today.isAfter(lastDate)) {
            scheduleGlobalRecalculation();
        }
    }

    public void scheduleGlobalRecalculation() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int count = priceRecommendationService.forceRecalculateAll(marketIndexService.getKnownMarketKeys());
                runtimeStateStore.setLastGlobalRecalcDate(LocalDate.now());
                logger.info("Completed global market recalculation for " + count + " market keys.");
            } catch (Exception exception) {
                logger.warning("Global market recalculation failed: " + exception.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    public void scheduleMarketRecalculation(String marketKey) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                priceRecommendationService.forceRecalculate(marketKey);
                logger.info("Recalculated market price for " + marketKey);
            } catch (Exception exception) {
                logger.warning("Single market recalculation failed for " + marketKey + ": " + exception.getMessage());
            }
        });
    }
}
