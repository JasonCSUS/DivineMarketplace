package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Persists and queries market index records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite-backed flattened market index table.
 *
 * Stores one flattened runtime entry per market key.
 */
public final class SQLiteMarketIndexStore {
    private static final String TABLE = "market_index";

    private final SQLiteStore sqliteStore;

    public SQLiteMarketIndexStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite market index table.", exception);
        }
    }

    public Map<String, FlattenedMarketIndexEntry> loadAll() {
        try {
            Map<String, FlattenedMarketIndexEntry> entries = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                FlattenedMarketIndexEntry decoded = decode(entry.getValue());
                entries.put(decoded.marketKey(), decoded);
            }
            return entries;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load market index entries from SQLite.", exception);
        }
    }

    public void replaceAll(Collection<FlattenedMarketIndexEntry> entries) {
        Map<String, String> encoded = new LinkedHashMap<>();
        for (FlattenedMarketIndexEntry entry : entries) {
            encoded.put(entry.marketKey(), encode(entry));
        }

        try {
            sqliteStore.replaceAll(TABLE, encoded);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace market index entries in SQLite.", exception);
        }
    }

    private String encode(FlattenedMarketIndexEntry entry) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, entry.recordType());
            SQLiteRecordCodecSupport.writeString(output, entry.marketKey());
            SQLiteRecordCodecSupport.writeString(output, entry.marketDisplayName());
            SQLiteRecordCodecSupport.writeString(output, entry.categoryId());
            SQLiteRecordCodecSupport.writeString(output, entry.itemType());
            SQLiteRecordCodecSupport.writeString(output, entry.requiredMaterial() == null ? null : entry.requiredMaterial().name());
            output.writeBoolean(entry.requiredCustomModelData() != null);
            if (entry.requiredCustomModelData() != null) {
                output.writeFloat(entry.requiredCustomModelData());
            }
            SQLiteRecordCodecSupport.writeString(output, entry.definitionState() == null ? null : entry.definitionState().name());
        });
    }

    private FlattenedMarketIndexEntry decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> {
            String recordType = SQLiteRecordCodecSupport.readString(input);
            String marketKey = SQLiteRecordCodecSupport.readString(input);
            String marketDisplayName = SQLiteRecordCodecSupport.readString(input);
            String categoryId = SQLiteRecordCodecSupport.readString(input);
            String itemType = SQLiteRecordCodecSupport.readString(input);
            String materialName = SQLiteRecordCodecSupport.readString(input);
            Material requiredMaterial = materialName == null ? null : Material.matchMaterial(materialName);
            Float requiredCustomModelData = input.readBoolean() ? input.readFloat() : null;
            CustomItemDefinitionState definitionState = input.available() > 0
                    ? CustomItemDefinitionState.fromStoredValue(SQLiteRecordCodecSupport.readString(input))
                    : null;

            return new FlattenedMarketIndexEntry(
                    recordType,
                    marketKey,
                    marketDisplayName,
                    categoryId == null || categoryId.isBlank() ? "unsorted" : categoryId,
                    itemType == null ? "" : itemType,
                    requiredMaterial,
                    requiredCustomModelData,
                    inferState(recordType, categoryId, definitionState)
            );
        });
    }

    private CustomItemDefinitionState inferState(String recordType, String categoryId, CustomItemDefinitionState decoded) {
        if (decoded != null) {
            return decoded;
        }
        if (!"CUSTOM".equalsIgnoreCase(recordType)) {
            return CustomItemDefinitionState.CONFIRMED;
        }
        if (categoryId != null && categoryId.equalsIgnoreCase("unsorted")) {
            return CustomItemDefinitionState.PROVISIONAL;
        }
        return CustomItemDefinitionState.CONFIRMED;
    }
}

