package divinejason.divinemarketplace.bootstrap;

/*
 * Layer : bootstrap / scheduler
 * Owns  : all Bukkit repeating task registrations
 * Calls : MarketRuntime (reads services/stores), plugin scheduler
 *
 * Centralises every recurring task so DivineMarketplace.java no longer
 * registers tasks inline.  Tasks are named for easier debug identification.
 *
 * Cleanup policy (from plan.txt):
 *   - Destructive storage deletion runs on startup and via admin commands only.
 *   - Scheduled tasks here are cheap monitoring/write-flush tasks, not bulk deletes.
 *   - Listing expiration (hourly) is an operational necessity, not heavy cleanup.
 */

import divinejason.divinemarketplace.auction.service.pricing.MarketRecalculationService;
import divinejason.divinemarketplace.config.ConfigService;
import java.sql.SQLException;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers and owns all recurring Bukkit scheduler tasks for DivineMarketplace.
 *
 * <p>Construct once after {@link MarketRuntime#enable()} returns, then call
 * {@link #registerAll()}.
 */
public final class MarketScheduler {

    private static final long TICKS_PER_SECOND  = 20L;
    private static final long TICKS_1_MINUTE    = TICKS_PER_SECOND * 60L;
    private static final long TICKS_1_HOUR      = TICKS_PER_SECOND * 60L * 60L;
    private static final long TICKS_30_MINUTES  = TICKS_PER_SECOND * 60L * 30L;
    private static final long TICKS_5_MINUTES   = TICKS_PER_SECOND * 60L * 5L;

    private final JavaPlugin plugin;
    private final MarketRuntime runtime;
    private final Logger logger;

    public MarketScheduler(JavaPlugin plugin, MarketRuntime runtime) {
        this.plugin  = plugin;
        this.runtime = runtime;
        this.logger  = plugin.getLogger();
    }

    /** Registers all recurring tasks.  Call once after MarketRuntime.enable(). */
    public void registerAll() {
        registerDailyRecalculation();
        registerHourlyListingExpiration();
        registerWriteBehindFlush();
        registerStoragePressureMonitor();
    }

    // -------------------------------------------------------------------------
    // Tasks
    // -------------------------------------------------------------------------

    /**
     * Startup + daily market recommendation recalculation.
     * Delegates entirely to MarketRecalculationService which batches work across ticks.
     */
    private void registerDailyRecalculation() {
        runtime.getMarketRecalculationService().scheduleStartupAndDailyChecks();
    }

    /**
     * Hourly pass that expires listings whose duration has elapsed.
     * Expired listings create item claims for their sellers automatically.
     */
    private void registerHourlyListingExpiration() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!runtime.isReady()) return;
            try {
                runtime.getListingService().expireDueListings(System.currentTimeMillis());
            } catch (RuntimeException e) {
                logger.warning("[Scheduler] Hourly listing expiration failed: " + e.getMessage());
            }
        }, TICKS_1_HOUR, TICKS_1_HOUR);
    }

    /**
     * Every minute: flush the shared SQLite write-behind queue.
     * High-risk gameplay actions update memory immediately and queue durable
     * mutations here instead of writing SQLite from the menu click path.
     */
    private void registerWriteBehindFlush() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!runtime.isReady()) return;
            try {
                runtime.getWriteBehindQueue().flushAsync();
            } catch (RuntimeException e) {
                logger.warning("[Scheduler] SQLite write-behind flush failed to start: " + e.getMessage());
            }
        }, TICKS_1_MINUTE, TICKS_1_MINUTE);
    }

    /**
     * Every 30 minutes: log a storage size snapshot for admin awareness.
     * This task is MONITORING ONLY — it does NOT delete rows.
     * Deletion happens on startup and via /market storage cleanup.
     */
    private void registerStoragePressureMonitor() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!runtime.isReady()) return;
            try {
                long totalBytes = runtime.getSqliteStore().databaseStorageSizeBytes();
                long adminBytes = runtime.getMarketEventService().estimatedPayloadBytes();
                long claimBytes = runtime.getItemClaimStore().estimatedPayloadBytes();

                long softMaxBytes = ConfigService.get().itemClaimsSoftMaxBytes();
                if (softMaxBytes > 0 && claimBytes >= softMaxBytes) {
                    notifyAdminsAboutClaimPressure(claimBytes, softMaxBytes);
                }

                logger.fine("[Storage] total=" + totalBytes + " B  admin=" + adminBytes + " B  claims=" + claimBytes + " B");
            } catch (RuntimeException e) {
                logger.warning("[Scheduler] Storage monitor failed: " + e.getMessage());
            }
        }, TICKS_5_MINUTES, TICKS_30_MINUTES);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void notifyAdminsAboutClaimPressure(long claimBytes, long softMaxBytes) {
        String log = "DivineMarketplace item-claim storage is above the soft limit ("
            + claimBytes + " B / " + softMaxBytes + " B). "
            + "Run /market storage cleanup or ask players to empty their claims.";
        logger.warning(log);

        String rich = "<yellow>DivineMarketplace:</yellow> <gray>Item-claim storage is above the soft limit. "
            + "Run <white>/market storage cleanup</white> or ask players to empty their claims.</gray>";
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("divinemarketplace.admin")) {
                player.sendRichMessage(rich);
            }
        }
    }
}
