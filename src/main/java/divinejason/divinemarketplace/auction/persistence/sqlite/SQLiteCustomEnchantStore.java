package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Persists and queries custom enchant records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal SQLite-backed custom enchant definition store for admin command use.
 */
public final class SQLiteCustomEnchantStore {
    private static final String TABLE = "custom_enchants";

    private final SQLiteStore sqliteStore;

    public SQLiteCustomEnchantStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite custom enchant table.", exception);
        }
    }

    public void upsert(String namespacedEnchantKey, String displayName, List<String> itemTokens) {
        try {
            sqliteStore.put(TABLE, normalizeKey(namespacedEnchantKey), encode(displayName, itemTokens));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write custom enchant definition to SQLite.", exception);
        }
    }

    public Map<String, CustomEnchantEntry> loadAll() {
        try {
            Map<String, CustomEnchantEntry> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                String normalizedKey = normalizeKey(entry.getKey());
                result.put(normalizedKey, decode(normalizedKey, entry.getValue()));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load custom enchant definitions from SQLite.", exception);
        }
    }

    private String normalizeKey(String namespacedEnchantKey) {
        return namespacedEnchantKey == null ? "" : namespacedEnchantKey.trim().toLowerCase(Locale.ROOT);
    }

    private String encode(String displayName, List<String> itemTokens) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, displayName);
            output.writeInt(itemTokens.size());
            for (String token : itemTokens) {
                SQLiteRecordCodecSupport.writeString(output, token);
            }
        });
    }

    private CustomEnchantEntry decode(String namespacedKey, String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> {
            String displayName = SQLiteRecordCodecSupport.readString(input);
            int size = input.readInt();
            List<String> itemTokens = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String token = SQLiteRecordCodecSupport.readString(input);
                if (token != null && !token.isBlank()) {
                    itemTokens.add(token);
                }
            }
            return new CustomEnchantEntry(namespacedKey, displayName == null ? namespacedKey : displayName, itemTokens);
        });
    }

    public record CustomEnchantEntry(String namespacedEnchantKey, String displayName, List<String> itemTokens) {
    }
}
