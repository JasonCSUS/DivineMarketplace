package divinejason.divinemarketplace.auction.persistence.sqlite;

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

    public void put(String marketKey, long recommendedPrice) {
        try {
            sqliteStore.put(TABLE, marketKey, Long.toString(Math.max(0L, recommendedPrice)));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write market price to SQLite.", exception);
        }
    }

    public void replaceAll(Map<String, Long> prices) {
        Map<String, String> encoded = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : prices.entrySet()) {
            encoded.put(entry.getKey(), Long.toString(Math.max(0L, entry.getValue())));
        }

        try {
            sqliteStore.replaceAll(TABLE, encoded);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace market prices in SQLite.", exception);
        }
    }
}
