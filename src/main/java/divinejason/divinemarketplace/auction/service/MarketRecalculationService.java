package divinejason.divinemarketplace.auction.service;


/*
 * File role: Contains marketplace service logic for market recalculation service.
 */
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.setup.MarketRuntimeStateStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Schedules daily recommended-price recalculation.
 *
 * This is not a backup service. The timer only checks whether the configured
 * recommendation interval is due, then processes market keys in config-sized
 * chunks so large markets do not recalculate every key in one run.
 */
public final class MarketRecalculationService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final FlattenedMarketIndexService marketIndexService;
    private final PriceRecommendationService priceRecommendationService;
    private final MarketRuntimeStateStore runtimeStateStore;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object queueLock = new Object();
    private final Deque<String> pendingGlobalMarketKeys = new ArrayDeque<>();
    private LocalDate pendingGlobalDate;

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
        long intervalTicks = Math.max(20L * 60L, ConfigService.get().marketRecalcIntervalMillis() / 50L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndScheduleDailyRecalculation, intervalTicks, intervalTicks);
    }

    public void checkAndScheduleDailyRecalculation() {
        LocalDate today = LocalDate.now();
        LocalDate lastDate = runtimeStateStore.getLastGlobalRecalcDate();
        if (lastDate == null || today.isAfter(lastDate) || hasPendingGlobalWork()) {
            scheduleGlobalRecalculation();
        }
    }

    public void scheduleGlobalRecalculation() {
        ensureGlobalQueue();

        if (!running.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                runNextGlobalChunk();
            } finally {
                running.set(false);
                if (hasPendingGlobalWork()) {
                    scheduleGlobalRecalculation();
                }
            }
        });
    }

    private void runNextGlobalChunk() {
        List<String> chunk = pollNextGlobalChunk();
        if (chunk.isEmpty()) {
            markGlobalCompleteIfQueueEmpty();
            return;
        }

        try {
            int count = priceRecommendationService.forceRecalculateAll(chunk);
            logger.info("Recalculated recommended prices for " + count + " market keys this chunk. Pending=" + pendingGlobalCount());
        } catch (Exception exception) {
            logger.warning("Global market recalculation chunk failed: " + exception.getMessage());
            requeueFront(chunk);
            return;
        }

        markGlobalCompleteIfQueueEmpty();
    }

    private void ensureGlobalQueue() {
        synchronized (queueLock) {
            LocalDate today = LocalDate.now();
            if (!pendingGlobalMarketKeys.isEmpty() && today.equals(pendingGlobalDate)) {
                return;
            }

            LocalDate lastDate = runtimeStateStore.getLastGlobalRecalcDate();
            if (lastDate != null && !today.isAfter(lastDate)) {
                return;
            }

            pendingGlobalMarketKeys.clear();
            pendingGlobalMarketKeys.addAll(marketIndexService.getKnownMarketKeys());
            pendingGlobalDate = today;
            logger.info("Queued daily recommended-price recalculation for " + pendingGlobalMarketKeys.size() + " market keys.");
        }
    }

    private List<String> pollNextGlobalChunk() {
        synchronized (queueLock) {
            int max = Math.max(1, ConfigService.get().marketRecalcItemsPerRun());
            List<String> chunk = new ArrayList<>(max);
            while (chunk.size() < max && !pendingGlobalMarketKeys.isEmpty()) {
                chunk.add(pendingGlobalMarketKeys.removeFirst());
            }
            return chunk;
        }
    }

    private void requeueFront(List<String> chunk) {
        synchronized (queueLock) {
            for (int i = chunk.size() - 1; i >= 0; i--) {
                pendingGlobalMarketKeys.addFirst(chunk.get(i));
            }
        }
    }

    private void markGlobalCompleteIfQueueEmpty() {
        synchronized (queueLock) {
            if (!pendingGlobalMarketKeys.isEmpty()) {
                return;
            }
            if (pendingGlobalDate != null) {
                runtimeStateStore.setLastGlobalRecalcDate(pendingGlobalDate);
                logger.info("Completed daily recommended-price recalculation for " + pendingGlobalDate + ".");
                pendingGlobalDate = null;
            }
        }
    }

    private boolean hasPendingGlobalWork() {
        synchronized (queueLock) {
            return !pendingGlobalMarketKeys.isEmpty();
        }
    }

    private int pendingGlobalCount() {
        synchronized (queueLock) {
            return pendingGlobalMarketKeys.size();
        }
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
