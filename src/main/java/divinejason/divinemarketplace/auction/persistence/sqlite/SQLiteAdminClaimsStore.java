package divinejason.divinemarketplace.auction.persistence.sqlite;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecordCodecSupport;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class SQLiteAdminClaimsStore {
    private static final String TABLE = "admin_claims";

    private final SQLiteStore sqliteStore;
    private final Map<String, AdminTransactionRecord> cacheById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteAdminClaimsStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite admin history table: " + TABLE, exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            try {
                cacheById.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    cacheById.put(entry.getKey(), decode(entry.getValue()));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload admin history from SQLite.", exception);
            }
        }
    }

    public void append(AdminTransactionRecord record) {
        synchronized (lock) {
            cacheById.put(record.transactionId(), record);
            try {
                sqliteStore.put(TABLE, record.transactionId(), encode(record));
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to append admin record to SQLite.", exception);
            }
        }
    }

    public List<AdminTransactionRecord> findByPlayer(UUID playerUuid, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = cacheById.values().stream()
                    .filter(record -> playerUuid.equals(record.sellerUuid()) || playerUuid.equals(record.buyerUuid()) || playerUuid.equals(record.ownerUuid()))
                    .sorted(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed())
                    .toList();
            return page(records, page, pageSize);
        }
    }

    public List<AdminTransactionRecord> findByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = cacheById.values().stream()
                    .filter(record -> record.timestampEpochMillis() >= startEpochMillis && record.timestampEpochMillis() <= endEpochMillis)
                    .sorted(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed())
                    .toList();
            return page(records, page, pageSize);
        }
    }

    public List<AdminTransactionRecord> findByMarketKey(String marketKey, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = cacheById.values().stream()
                    .filter(record -> marketKey.equals(record.marketKey()))
                    .sorted(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed())
                    .toList();
            return page(records, page, pageSize);
        }
    }

    public Optional<AdminTransactionRecord> findByTransactionId(String transactionId) {
        synchronized (lock) {
            return Optional.ofNullable(cacheById.get(transactionId));
        }
    }

    /**
     * SQLite migration note:
     * - per-file max-size purging no longer maps cleanly to shared DB storage
     * - this is intentionally a no-op for now
     */
    public void purgeOldestIfOverMaxSize() {
        // no-op during SQLite migration
    }

    private List<AdminTransactionRecord> page(List<AdminTransactionRecord> input, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= input.size()) {
            return List.of();
        }
        int end = Math.min(input.size(), start + pageSize);
        return List.copyOf(input.subList(start, end));
    }

    private String encode(AdminTransactionRecord record) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, record.transactionId());
            output.writeLong(record.timestampEpochMillis());
            SQLiteRecordCodecSupport.writeString(output, record.transactionType().name());
            SQLiteRecordCodecSupport.writeString(output, record.listingId());
            SQLiteRecordCodecSupport.writeUuid(output, record.sellerUuid());
            SQLiteRecordCodecSupport.writeUuid(output, record.buyerUuid());
            SQLiteRecordCodecSupport.writeUuid(output, record.ownerUuid());
            SQLiteRecordCodecSupport.writeString(output, record.marketKey());
            SQLiteRecordCodecSupport.writeString(output, record.marketDisplayName());
            SQLiteRecordCodecSupport.writeString(output, record.categoryId());
            SQLiteRecordCodecSupport.writeString(output, record.itemSummary());
            output.writeInt(record.amount());
            output.writeLong(record.totalPrice());
            output.writeLong(record.unitPrice());
            SQLiteRecordCodecSupport.writeString(output, record.status());
            SQLiteRecordCodecSupport.writeString(output, record.reason());
        });
    }

    private AdminTransactionRecord decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new AdminTransactionRecord(
                SQLiteRecordCodecSupport.readString(input),
                input.readLong(),
                AdminTransactionType.valueOf(SQLiteRecordCodecSupport.readString(input)),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                input.readInt(),
                input.readLong(),
                input.readLong(),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input)
        ));
    }
}
