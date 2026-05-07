package divinejason.divinemarketplace.auction.service.event;

/*
 * Layer : service
 * Owns  : canonical market event write/read interface
 * Calls : nothing — interface only
 */


/*
 * File role: Defines the canonical market event service contract used by purchase, listing, claim, history, admin, and pricing services.
 */
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Canonical market event service.
 *
 * Write path: callers use appendInMemory + putMutation to integrate with the
 * existing SQLiteWriteBatch pattern and register rollback actions.
 *
 * Read path:
 * - getSaleHistory* returns SaleRecord projections of BUY events for player-facing history.
 * - findBy* returns raw MarketEventRecord for admin audit queries.
 * - Pricing uses getRecentSalesForMarketKey for recommendation calculations.
 *
 * Maintenance path: maintenancePurgeEventsOlderThan(cutoff) deletes events older than the configured retention window.
 */
public interface MarketEventService {

    // -------------------------------------------------------------------------
    // Write helpers — used by service write-batch pattern
    // -------------------------------------------------------------------------

    /** Appends event to the in-memory store and updates read indexes. */
    void appendInMemory(MarketEventRecord event);

    /** Removes event from the in-memory store and read indexes. Used by rollback actions. */
    void deleteInMemory(String eventId);

    /** Returns a write-behind mutation for the given event. Enqueue in the caller's SQLiteWriteBatch. */
    SQLiteMutation putMutation(MarketEventRecord event);

    /** Returns a delete mutation for the given event id. */
    SQLiteMutation deleteMutation(String eventId);

    // -------------------------------------------------------------------------
    // Player-facing sale history — BUY events where itemSnapshot != null
    // -------------------------------------------------------------------------

    List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis);

    List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize);

    // -------------------------------------------------------------------------
    // Admin / raw event queries
    // -------------------------------------------------------------------------

    List<MarketEventRecord> findByType(MarketEventType type, int page, int pageSize);

    List<MarketEventRecord> findByPlayer(UUID playerUuid, int page, int pageSize);

    List<MarketEventRecord> findByPlayerAndType(UUID playerUuid, MarketEventType type, int page, int pageSize);

    List<MarketEventRecord> findByPlayerAndTypes(UUID playerUuid, Collection<MarketEventType> types, int page, int pageSize);

    List<MarketEventRecord> findByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize);

    List<MarketEventRecord> findByMarketKey(String marketKey, int page, int pageSize);

    Optional<MarketEventRecord> findById(String eventId);

    // -------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------

    /** Purges all events older than cutoffEpochMillis, then reloads the index. Returns number purged. */
    int maintenancePurgeEventsOlderThan(long cutoffEpochMillis);

    /** Total events held in memory. */
    int countAll();

    /** Rough payload estimate for storage pressure reporting. */
    long estimatedPayloadBytes();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    void loadFromStorage();

    void reload();
}
