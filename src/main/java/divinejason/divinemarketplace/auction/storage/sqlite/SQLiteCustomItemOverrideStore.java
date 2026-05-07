package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries custom item override records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.CustomItemOverrideRecord;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class SQLiteCustomItemOverrideStore {
    private static final String TABLE = "custom_item_overrides";
    private final SQLiteStore sqliteStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final Map<String, CustomItemOverrideRecord> cache = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteCustomItemOverrideStore(SQLiteStore sqliteStore, SQLiteWriteBehindQueue writeBehindQueue) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite custom item override table.", exception);
        }
    }

    public void loadFromStorage() {
        synchronized (lock) {
            cache.clear();
            try {
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    CustomItemOverrideRecord record = decode(entry.getValue());
                    cache.put(record.signature(), record);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load custom item overrides from SQLite.", exception);
            }
        }
    }

    public void reload() {
        loadFromStorage();
    }

    public CustomItemOverrideRecord findBySignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        synchronized (lock) {
            return cache.get(signature);
        }
    }

    public void putTreatAsVanilla(String signature, String note) {
        CustomItemOverrideRecord record = new CustomItemOverrideRecord(
                signature, "TREAT_AS_VANILLA", note == null ? "" : note, System.currentTimeMillis());
        synchronized (lock) {
            cache.put(signature, record);
        }
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("custom item override " + signature)
                .add(SQLiteMutation.put(TABLE, signature, encode(record)))
                .build());
    }

    public void delete(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        synchronized (lock) {
            cache.remove(signature);
        }
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("delete custom item override " + signature)
                .add(SQLiteMutation.delete(TABLE, signature))
                .build());
    }

    private String encode(CustomItemOverrideRecord record) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, record.signature());
            SQLiteRecordCodecSupport.writeString(output, record.mode());
            SQLiteRecordCodecSupport.writeString(output, record.note());
            output.writeLong(record.createdAtEpochMillis());
        });
    }

    private CustomItemOverrideRecord decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new CustomItemOverrideRecord(
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                input.readLong()
        ));
    }
}
