package divinejason.divinemarketplace.auction.persistence.sqlite;

import divinejason.divinemarketplace.auction.model.MarketTrainingParticipation;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecordCodecSupport;
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
     * SQLite migration note:
     * - size-based file purging is no longer a good fit for shared DB storage
     * - this is intentionally a no-op for now
     */
    public void purgeOldestIfOverMaxSize() {
        // no-op during SQLite migration
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
