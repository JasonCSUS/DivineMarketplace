package divinejason.divinemarketplace.auction.storage.sqlite;

/*
 * Layer : store
 * Owns  : market_events SQLite table
 * Calls : SQLiteStore (storage layer only)
 *
 * Single canonical event table replacing the separate sales and admin_history tables.
 * All market actions (list, buy, cancel, expire, claim, relist) write one event row here.
 * Player sale history and admin audit history are in-memory projections over this table.
 *
 * Retention: rows are purged on startup and via /market storage cleanup,
 * not during active gameplay.
 */


/*
 * File role: Persists and queries canonical market event records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.model.MarketTrainingParticipation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import java.sql.SQLException;
import java.util.*;
import org.bukkit.inventory.ItemStack;

/**
 * Memory-first store for canonical market events.
 *
 * All market actions write one MarketEventRecord here.
 * Player sale history and admin audit history are projections built by DefaultMarketEventService.
 *
 * Threading: all public mutating methods synchronize on {@code lock}.
 */
public final class SQLiteMarketEventStore {

    private static final String TABLE = "market_events";

    private final SQLiteStore sqliteStore;
    private final Map<String, MarketEventRecord> cacheById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteMarketEventStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize market_events table.", exception);
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /** Appends one record to memory only. Callers must enqueue the matching mutation separately. */
    public void appendInMemory(MarketEventRecord record) {
        Objects.requireNonNull(record, "record");
        synchronized (lock) {
            cacheById.put(record.eventId(), record);
        }
    }

    public void deleteInMemory(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        synchronized (lock) {
            cacheById.remove(eventId);
        }
    }

    public SQLiteMutation putMutation(MarketEventRecord record) {
        Objects.requireNonNull(record, "record");
        return SQLiteMutation.put(TABLE, record.eventId(), encode(record));
    }

    public SQLiteMutation deleteMutation(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return SQLiteMutation.delete(TABLE, eventId);
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** All events in insertion order. Used by DefaultMarketEventService to rebuild its indexes. */
    public List<MarketEventRecord> getAllEvents() {
        synchronized (lock) {
            return List.copyOf(cacheById.values());
        }
    }

    public Optional<MarketEventRecord> findById(String eventId) {
        if (eventId == null || eventId.isBlank()) return Optional.empty();
        synchronized (lock) {
            return Optional.ofNullable(cacheById.get(eventId));
        }
    }

    // -------------------------------------------------------------------------
    // Reload / retention
    // -------------------------------------------------------------------------

    public void loadFromStorage() {
        synchronized (lock) {
            try {
                cacheById.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    MarketEventRecord record = decode(entry.getValue());
                    cacheById.put(entry.getKey(), record);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load market_events from SQLite.", exception);
            }
        }
    }

    public void reload() {
        loadFromStorage();
    }

    /**
     * Purges all events with a timestamp before {@code cutoffEpochMillis}.
     * Returns the number of rows deleted. Called on startup and via admin cleanup command.
     */
    public int maintenancePurgeEventsOlderThan(long cutoffEpochMillis) {
        synchronized (lock) {
            try {
                List<String> toDelete = cacheById.entrySet().stream()
                        .filter(e -> e.getValue().timestampEpochMillis() < cutoffEpochMillis)
                        .map(Map.Entry::getKey)
                        .toList();
                int deleted = 0;
                for (String id : toDelete) {
                    if (sqliteStore.delete(TABLE, id)) {
                        cacheById.remove(id);
                        deleted++;
                    }
                }
                return deleted;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to purge market_events rows.", exception);
            }
        }
    }

    /** Rough payload estimate for storage pressure reporting. */
    public long estimatedPayloadBytes() {
        try {
            return sqliteStore.tablePayloadSizeBytes(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to estimate market_events size.", exception);
        }
    }

    // -------------------------------------------------------------------------
    // Codec
    // -------------------------------------------------------------------------

    private String encode(MarketEventRecord r) {
        return SQLiteRecordCodecSupport.encode(out -> {
            SQLiteRecordCodecSupport.writeString(out, r.eventId());
            out.writeLong(r.timestampEpochMillis());
            SQLiteRecordCodecSupport.writeString(out, r.eventType().name());
            SQLiteRecordCodecSupport.writeString(out, r.listingId());
            SQLiteRecordCodecSupport.writeUuid(out, r.sellerUuid());
            SQLiteRecordCodecSupport.writeUuid(out, r.buyerUuid());
            SQLiteRecordCodecSupport.writeUuid(out, r.ownerUuid());
            SQLiteRecordCodecSupport.writeString(out, r.marketKey());
            SQLiteRecordCodecSupport.writeString(out, r.marketDisplayName());
            SQLiteRecordCodecSupport.writeString(out, r.categoryId());
            SQLiteRecordCodecSupport.writeString(out, r.itemSummary());
            SQLiteRecordCodecSupport.writeItemStack(out, r.itemSnapshot()); // null-safe: writes false boolean if null
            out.writeInt(r.amount());
            out.writeLong(r.totalPrice());
            out.writeLong(r.unitPrice());
            SQLiteRecordCodecSupport.writeString(out, r.status());
            SQLiteRecordCodecSupport.writeString(out, r.reason());
            SQLiteRecordCodecSupport.writeString(out, r.trainingParticipation() != null
                    ? r.trainingParticipation().name() : null);
        });
    }

    private MarketEventRecord decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, in -> {
            String eventId = SQLiteRecordCodecSupport.readString(in);
            long timestamp = in.readLong();
            MarketEventType type = MarketEventType.valueOf(SQLiteRecordCodecSupport.readString(in));
            String listingId = SQLiteRecordCodecSupport.readString(in);
            UUID sellerUuid = SQLiteRecordCodecSupport.readUuid(in);
            UUID buyerUuid = SQLiteRecordCodecSupport.readUuid(in);
            UUID ownerUuid = SQLiteRecordCodecSupport.readUuid(in);
            String marketKey = SQLiteRecordCodecSupport.readString(in);
            String marketDisplayName = SQLiteRecordCodecSupport.readString(in);
            String categoryId = SQLiteRecordCodecSupport.readString(in);
            String itemSummary = SQLiteRecordCodecSupport.readString(in);
            ItemStack itemSnapshot = SQLiteRecordCodecSupport.readItemStack(in);
            int amount = in.readInt();
            long totalPrice = in.readLong();
            long unitPrice = in.readLong();
            String status = SQLiteRecordCodecSupport.readString(in);
            String reason = SQLiteRecordCodecSupport.readString(in);
            String trainingStr = SQLiteRecordCodecSupport.readString(in);
            MarketTrainingParticipation training = trainingStr != null
                    ? MarketTrainingParticipation.valueOf(trainingStr) : null;
            return new MarketEventRecord(
                    eventId, timestamp, type, listingId, sellerUuid, buyerUuid, ownerUuid,
                    marketKey, marketDisplayName, categoryId, itemSummary, itemSnapshot,
                    amount, totalPrice, unitPrice, status, reason, training);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

}
