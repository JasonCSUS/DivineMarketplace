package divinejason.divinemarketplace.auction.service.storage;

/*
 * Layer : service / storage maintenance
 * Owns  : destructive retention cleanup and storage-size reporting helpers
 * Calls : SQLite stores and SQLiteStore only
 * Avoids: GUI, commands, Bukkit event listeners, and command parsing
 *
 * This service keeps retention logic out of DivineMarketplace.java.  Startup
 * and /market storage cleanup both call this one file, so row deletion policy is
 * easy to audit before touching storage behavior.
 */

import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import divinejason.divinemarketplace.util.PerfTimer;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Logger;

public final class StorageMaintenanceService {

    /**
     * Immutable result returned by startup and admin-triggered cleanup passes.
     * Byte values include the main SQLite file plus WAL/SHM sidecars.
     */
    public record StorageMaintenanceResult(
            long beforeBytes,
            long afterBytes,
            int purgedEvents,
            int purgedItemClaims
    ) {
        public int totalPurged() { return purgedEvents + purgedItemClaims; }
    }

    private final Logger logger;
    private final SQLiteStore sqliteStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final MarketEventService marketEventService;
    private final SQLiteItemClaimStore itemClaimStore;

    public StorageMaintenanceService(
            Logger logger,
            SQLiteStore sqliteStore,
            SQLiteWriteBehindQueue writeBehindQueue,
            MarketEventService marketEventService,
            SQLiteItemClaimStore itemClaimStore
    ) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
    }

    /**
     * Deletes stale market_events rows and abandoned item claims,
     * then compacts SQLite if anything was removed.
     *
     * This is the ONLY designated purge path. Size-based triggers on normal runtime
     * paths must not be added here or elsewhere.
     *
     * Retention policy in effect:
     * - Market events: time-based (marketEventRetentionDays config, 0 = keep forever).
     * - Abandoned item claims: size-gated by itemClaimsSoftMaxMb + age-gated by abandonedItemClaimDays.
     */
    public StorageMaintenanceResult runRetentionPass() {
        return writeBehindQueue.runExclusiveMaintenance(this::runRetentionPassExclusive);
    }

    private StorageMaintenanceResult runRetentionPassExclusive() {
        boolean perf = PerfTimer.enabled();
        long startNanos = perf ? System.nanoTime() : 0;

        long beforeBytes = sqliteStorageBytes();

        int purgedEvents = purgeMarketEvents();
        int purgedClaims = purgeAbandonedItemClaims();

        int total = purgedEvents + purgedClaims;
        if (total > 0) {
            try {
                sqliteStore.checkpointAndVacuum();
            } catch (SQLException exception) {
                logger.warning("Rows removed but SQLite VACUUM failed: " + exception.getMessage());
            }
        }

        long afterBytes = sqliteStorageBytes();
        StorageMaintenanceResult result = new StorageMaintenanceResult(
                beforeBytes, afterBytes, purgedEvents, purgedClaims);

        if (total > 0) {
            logger.info("Storage retention removed " + total + " row(s)."
                    + "  events=" + purgedEvents
                    + "  claims=" + purgedClaims
                    + "  storage=" + beforeBytes + " -> " + afterBytes + " B");
        }
        if (perf) {
            logger.info("[DivineMarketplace][perf] storage maintenance purgedEvents=" + purgedEvents
                    + " purgedClaims=" + purgedClaims
                    + " time=" + (System.nanoTime() - startNanos) / 1_000_000 + "ms");
        }
        return result;
    }

    public long sqliteStorageBytes() {
        return sqliteStore.databaseStorageSizeBytes();
    }

    public long marketEventPayloadBytes() {
        return marketEventService.estimatedPayloadBytes();
    }

    public long itemClaimPayloadBytes() {
        return itemClaimStore.estimatedPayloadBytes();
    }

    private int purgeMarketEvents() {
        long retentionMillis = ConfigService.get().marketEventRetentionMillis();
        if (retentionMillis <= 0L) return 0;
        return marketEventService.maintenancePurgeEventsOlderThan(System.currentTimeMillis() - retentionMillis);
    }

    private int purgeAbandonedItemClaims() {
        long softMax = ConfigService.get().itemClaimsSoftMaxBytes();
        long claimSize = itemClaimPayloadBytes();
        if (softMax <= 0L || claimSize < softMax) {
            return 0;
        }
        return itemClaimStore.maintenancePurgeOldestAbandonedClaims(System.currentTimeMillis());
    }
}
