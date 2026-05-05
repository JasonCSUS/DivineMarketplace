package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Persists and queries custom item override records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.CustomItemOverrideRecord;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.util.Objects;

public final class SQLiteCustomItemOverrideStore {
    private static final String TABLE = "custom_item_overrides";
    private final SQLiteStore sqliteStore;

    public SQLiteCustomItemOverrideStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite custom item override table.", exception);
        }
    }

    public CustomItemOverrideRecord findBySignature(String signature) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        try {
            return sqliteStore.get(TABLE, signature).map(value -> decode(value)).orElse(null);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load custom item override.", exception);
        }
    }

    public void putTreatAsVanilla(String signature, String note) {
        try {
            sqliteStore.put(TABLE, signature, encode(new CustomItemOverrideRecord(signature, "TREAT_AS_VANILLA", note == null ? "" : note, System.currentTimeMillis())));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write custom item override.", exception);
        }
    }

    public void delete(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        try {
            sqliteStore.delete(TABLE, signature);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete custom item override.", exception);
        }
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
