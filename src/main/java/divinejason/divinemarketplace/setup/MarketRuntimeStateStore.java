package divinejason.divinemarketplace.setup;


/*
 * File role: Persists small runtime markers such as last maintenance timestamps in SQLite-backed runtime state.
 */
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * SQLite-backed tiny runtime state store.
 */
public final class MarketRuntimeStateStore {
    private static final String TABLE = "runtime_state";
    private static final String KEY_LAST_GLOBAL_RECALC_DATE = "last_global_recalc_date";

    private final SQLiteStore sqliteStore;

    public MarketRuntimeStateStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite runtime state table.", exception);
        }
    }

    public synchronized LocalDate getLastGlobalRecalcDate() {
        try {
            return sqliteStore.get(TABLE, KEY_LAST_GLOBAL_RECALC_DATE)
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .orElse(null);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read runtime state from SQLite.", exception);
        }
    }

    public synchronized void setLastGlobalRecalcDate(LocalDate date) {
        try {
            sqliteStore.put(TABLE, KEY_LAST_GLOBAL_RECALC_DATE, Objects.requireNonNull(date, "date").toString());
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write runtime state to SQLite.", exception);
        }
    }
}
