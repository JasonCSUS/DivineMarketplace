package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Persists and queries sales records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.MarketTrainingParticipation;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecordCodecSupport;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite-backed exact sale history store.
 */
public final class SQLiteSalesStore {
    private static final String TABLE = "sales";

    private final SQLiteStore sqliteStore;
    private final Map<String, SaleRecord> cacheById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteSalesStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite sales table.", exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            try {
                cacheById.clear();
                cacheById.putAll(loadAllFromDb());
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload sales from SQLite.", exception);
            }
        }
    }

    public void append(SaleRecord saleRecord) {
        synchronized (lock) {
            String id = saleRecord.soldAtEpochMillis() + "-" + UUID.randomUUID();
            cacheById.put(id, saleRecord);
            try {
                sqliteStore.put(TABLE, id, encode(saleRecord));
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to append sale record to SQLite.", exception);
            }
        }
    }

    public List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis) {
        synchronized (lock) {
            long cutoff = System.currentTimeMillis() - lookbackMillis;
            return cacheById.values().stream()
                    .filter(record -> record.marketKey().equals(marketKey))
                    .filter(record -> record.soldAtEpochMillis() >= cutoff)
                    .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis))
                    .toList();
        }
    }

    public List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize) {
        synchronized (lock) {
            List<SaleRecord> filtered = cacheById.values().stream()
                    .filter(record -> record.marketKey().equals(marketKey))
                    .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis).reversed())
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<SaleRecord> getAllSales() {
        synchronized (lock) {
            return List.copyOf(cacheById.values());
        }
    }

    /**
     * Trims exact sale history using a logical per-table byte limit. The old
     * binary implementation capped a dedicated history file; in SQLite all
     * modules share one database, so this uses the encoded payload size for the
     * sales table as the equivalent retention pressure signal.
     *
     * @return number of sale records removed from SQLite and the in-memory cache
     */
    public int purgeOldestIfOverMaxSize() {
        return purgeOldestUntilUnderBytes(ConfigService.get().salesHistoryMaxBytes());
    }

    public int purgeOldestUntilUnderBytes(long maxBytes) {
        if (maxBytes <= 0L) {
            return 0;
        }

        synchronized (lock) {
            try {
                long currentBytes = sqliteStore.tablePayloadSizeBytes(TABLE);
                if (currentBytes <= maxBytes) {
                    return 0;
                }

                int deleted = 0;
                List<Map.Entry<String, SaleRecord>> oldest = cacheById.entrySet().stream()
                        .sorted(Comparator.comparingLong(entry -> entry.getValue().soldAtEpochMillis()))
                        .toList();

                for (Map.Entry<String, SaleRecord> entry : oldest) {
                    String encoded = encode(entry.getValue());
                    if (sqliteStore.delete(TABLE, entry.getKey())) {
                        cacheById.remove(entry.getKey());
                        currentBytes -= estimatedPayloadBytes(entry.getKey(), encoded);
                        deleted++;
                    }
                    if (currentBytes <= maxBytes) {
                        break;
                    }
                }
                return deleted;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to purge old sale history from SQLite.", exception);
            }
        }
    }

    public long estimatedPayloadBytes() {
        try {
            return sqliteStore.tablePayloadSizeBytes(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to estimate sale-history table size.", exception);
        }
    }

    private long estimatedPayloadBytes(String id, String encodedValue) {
        return Math.max(0, id.length()) + Math.max(0, encodedValue.length()) + 8L;
    }

    private Map<String, SaleRecord> loadAllFromDb() throws SQLException {
        Map<String, SaleRecord> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
            result.put(entry.getKey(), decode(entry.getValue()));
        }
        return result;
    }

    private List<SaleRecord> page(List<SaleRecord> input, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= input.size()) {
            return List.of();
        }
        int end = Math.min(input.size(), start + pageSize);
        return List.copyOf(input.subList(start, end));
    }

    private String encode(SaleRecord record) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, record.marketKey());
            SQLiteRecordCodecSupport.writeString(output, record.marketDisplayName());
            SQLiteRecordCodecSupport.writeItemStack(output, record.soldItemSnapshot());
            output.writeInt(record.amountPurchased());
            output.writeLong(record.unitPrice());
            output.writeLong(record.soldAtEpochMillis());
            SQLiteRecordCodecSupport.writeString(output, record.marketTrainingParticipation().name());
        });
    }

    private SaleRecord decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new SaleRecord(
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readItemStack(input),
                input.readInt(),
                input.readLong(),
                input.readLong(),
                MarketTrainingParticipation.valueOf(SQLiteRecordCodecSupport.readString(input))
        ));
    }
}
