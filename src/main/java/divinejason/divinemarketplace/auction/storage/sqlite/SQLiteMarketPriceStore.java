package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries market price records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite-backed current recommended price table.
 */
public final class SQLiteMarketPriceStore {
    private static final String TABLE = "market_prices";

    private final SQLiteStore sqliteStore;

    public SQLiteMarketPriceStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite market prices table.", exception);
        }
    }

    public Map<String, Long> loadAll() {
        try {
            Map<String, Long> prices = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                long price = 0L;
                try {
                    price = Long.parseLong(entry.getValue());
                } catch (NumberFormatException ignored) {
                }
                prices.put(entry.getKey(), price);
            }
            return prices;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load market prices from SQLite.", exception);
        }
    }

    /** Returns a queued PUT mutation for a single price — does not write to SQLite. */
    public SQLiteMutation putMutation(String marketKey, long recommendedPrice) {
        return SQLiteMutation.put(TABLE, marketKey, Long.toString(Math.max(0L, recommendedPrice)));
    }
}
