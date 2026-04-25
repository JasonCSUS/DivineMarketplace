package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory cache of custom item definitions with write-through persistence.
 */
public final class DefaultCustomItemRegistry implements CustomItemRegistry {
    private final CustomItemDataSource dataSource;

    private final Map<String, CustomItemDefinition> byItemType = new LinkedHashMap<>();
    private final Map<String, CustomItemDefinition> byMaterialAndCmd = new LinkedHashMap<>();

    public DefaultCustomItemRegistry(CustomItemDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        reload();
    }

    @Override
    public synchronized CustomItemDefinition findByItemType(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return null;
        }
        return byItemType.get(itemType);
    }

    @Override
    public synchronized CustomItemDefinition findByMaterialAndCustomModelData(Material material, Float requiredCustomModelData) {
        if (material == null || requiredCustomModelData == null) {
            return null;
        }
        return byMaterialAndCmd.get(fallbackKey(material, requiredCustomModelData));
    }

    @Override
    public synchronized CustomItemDefinition upsertDefinition(CustomItemDefinition definition) {
        CustomItemDefinition persisted = dataSource.upsertDefinition(definition);
        putInIndexes(persisted);
        return persisted;
    }

    @Override
    public synchronized void reload() {
        byItemType.clear();
        byMaterialAndCmd.clear();

        for (CustomItemDefinition definition : dataSource.loadDefinitions().values()) {
            putInIndexes(definition);
        }
    }

    private void putInIndexes(CustomItemDefinition definition) {
        byItemType.put(definition.itemType(), definition);

        if (definition.requiredMaterial() != null && definition.requiredCustomModelData() != null) {
            byMaterialAndCmd.put(
                    fallbackKey(definition.requiredMaterial(), definition.requiredCustomModelData()),
                    definition
            );
        }
    }

    private String fallbackKey(Material material, Float requiredCustomModelData) {
        return material.name() + "|" + Float.toString(requiredCustomModelData);
    }
}
