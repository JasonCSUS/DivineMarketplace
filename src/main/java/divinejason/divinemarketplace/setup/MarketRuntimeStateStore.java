package divinejason.divinemarketplace.setup;


/*
 * File role: Persists small runtime markers such as last maintenance timestamps in SQLite-backed runtime state.
 */
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite-backed tiny runtime state store.
 *
 * <p>Reads are backed by a lazy in-memory cache so that callers see the value
 * set by {@link #setLastGlobalRecalcDateMutation} immediately, even before the
 * write-behind queue has flushed to SQLite.</p>
 */
public final class MarketRuntimeStateStore {
    private static final String TABLE = "runtime_state";
    private static final String KEY_LAST_GLOBAL_RECALC_DATE = "last_global_recalc_date";

    private final SQLiteStore sqliteStore;

    /** null = not yet loaded from SQLite; Optional.empty() = loaded, no value present */
    private Optional<LocalDate> cachedRecalcDate = null;

    public MarketRuntimeStateStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite runtime state table.", exception);
        }
    }

    public synchronized LocalDate getLastGlobalRecalcDate() {
        if (cachedRecalcDate == null) {
            try {
                LocalDate loaded = sqliteStore.get(TABLE, KEY_LAST_GLOBAL_RECALC_DATE)
                        .filter(value -> !value.isBlank())
                        .map(LocalDate::parse)
                        .orElse(null);
                cachedRecalcDate = Optional.ofNullable(loaded);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to read runtime state from SQLite.", exception);
            }
        }
        return cachedRecalcDate.orElse(null);
    }

    /**
     * Updates the in-memory cache immediately and returns a mutation for the write-behind queue.
     * Callers must enqueue the returned mutation; this method does not write to SQLite directly.
     */
    public synchronized SQLiteMutation setLastGlobalRecalcDateMutation(LocalDate date) {
        cachedRecalcDate = Optional.of(Objects.requireNonNull(date, "date"));
        return SQLiteMutation.put(TABLE, KEY_LAST_GLOBAL_RECALC_DATE, date.toString());
    }
}
