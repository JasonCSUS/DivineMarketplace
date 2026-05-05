package divinejason.divinemarketplace.auction.service;


/*
 * File role: Contains marketplace service logic for sqlite custom item data source.
 */
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMarketIndexStore;
import divinejason.divinemarketplace.config.ConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite-backed custom item definition storage.
 *
 * Runtime custom item definitions now come from the live flattened market index
 * table instead of rereading custom_items.yml on every reload.
 */
public final class SQLiteCustomItemDataSource implements CustomItemDataSource {
    private final SQLiteMarketIndexStore marketIndexStore;

    public SQLiteCustomItemDataSource(SQLiteMarketIndexStore marketIndexStore) {
        this.marketIndexStore = Objects.requireNonNull(marketIndexStore, "marketIndexStore");
    }

    @Override
    public Map<String, CustomItemDefinition> loadDefinitions() {
        Map<String, CustomItemDefinition> definitions = new LinkedHashMap<>();

        for (FlattenedMarketIndexEntry entry : marketIndexStore.loadAll().values()) {
            if (!entry.isCustom()) {
                continue;
            }
            if (entry.itemType() == null || entry.itemType().isBlank()) {
                continue;
            }
            if (entry.requiredMaterial() == null) {
                continue;
            }

            CustomItemDefinition definition = new CustomItemDefinition(
                    entry.itemType(),
                    entry.requiredMaterial(),
                    entry.requiredCustomModelData(),
                    entry.marketDisplayName(),
                    entry.categoryId(),
                    inferState(entry)
            );
            definitions.put(definition.itemType(), definition);
        }

        return definitions;
    }

    @Override
    public CustomItemDefinition upsertDefinition(CustomItemDefinition definition) {
        Map<String, FlattenedMarketIndexEntry> allEntries = new LinkedHashMap<>(marketIndexStore.loadAll());
        FlattenedMarketIndexEntry entry = new FlattenedMarketIndexEntry(
                "CUSTOM",
                definition.itemType(),
                definition.marketDisplayName(),
                normalizeCategory(definition.categoryId()),
                definition.itemType(),
                definition.requiredMaterial(),
                definition.requiredCustomModelData(),
                definition.state()
        );
        allEntries.put(entry.marketKey(), entry);
        marketIndexStore.replaceAll(allEntries.values());
        return definition;
    }

    private CustomItemDefinitionState inferState(FlattenedMarketIndexEntry entry) {
        CustomItemDefinitionState stored = entry.definitionState();
        if (stored != null) {
            return stored;
        }
        String categoryId = entry.categoryId();
        if (categoryId != null && categoryId.equalsIgnoreCase(ConfigService.get().unknownCustomModelCategory())) {
            return CustomItemDefinitionState.PROVISIONAL;
        }
        return CustomItemDefinitionState.CONFIRMED;
    }

    private String normalizeCategory(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? "unsorted" : categoryId;
    }
}
