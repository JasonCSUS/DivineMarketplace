package divinejason.divinemarketplace.auction.service;


/*
 * File role: Implements custom item registry behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * In-memory cache of custom item definitions with write-through persistence.
 *
 * Collision protection:
 * - itemType must not silently point at two different material/CMD identities
 * - material+CMD fallback must not silently point at two different itemTypes
 */
public final class DefaultCustomItemRegistry implements CustomItemRegistry {
    private final CustomItemDataSource dataSource;
    private final CustomItemCollisionLogService collisionLogService;
    private final Logger logger;

    private final Map<String, CustomItemDefinition> byItemType = new LinkedHashMap<>();
    private final Map<String, CustomItemDefinition> byMaterialAndCmd = new LinkedHashMap<>();

    public DefaultCustomItemRegistry(Logger logger, CustomItemDataSource dataSource, CustomItemCollisionLogService collisionLogService) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.collisionLogService = Objects.requireNonNull(collisionLogService, "collisionLogService");
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
        CustomItemDefinition existingByType = byItemType.get(definition.itemType());
        if (existingByType != null && !sameIdentity(existingByType, definition)) {
            logger.warning(() -> "Custom item collision by itemType '" + definition.itemType()
                    + "': existing=" + describe(existingByType)
                    + " new=" + describe(definition)
                    + ". Keeping existing definition.");
            collisionLogService.recordCollision("itemType", existingByType, definition, "kept existing definition");
            return existingByType;
        }

        if (definition.requiredMaterial() != null && definition.requiredCustomModelData() != null) {
            CustomItemDefinition existingByFallback = byMaterialAndCmd.get(fallbackKey(definition.requiredMaterial(), definition.requiredCustomModelData()));
            if (existingByFallback != null && !existingByFallback.itemType().equals(definition.itemType())) {
                logger.warning(() -> "Custom item collision by material+CMD '" + fallbackKey(definition.requiredMaterial(), definition.requiredCustomModelData())
                        + "': existing=" + describe(existingByFallback)
                        + " new=" + describe(definition)
                        + ". Keeping existing definition.");
                collisionLogService.recordCollision("material+CMD", existingByFallback, definition, "kept existing definition");
                return existingByFallback;
            }
        }

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
        CustomItemDefinition existingByType = byItemType.get(definition.itemType());
        if (existingByType != null && !sameIdentity(existingByType, definition)) {
            logger.warning(() -> "Ignoring loaded conflicting custom itemType '" + definition.itemType()
                    + "': existing=" + describe(existingByType)
                    + " incoming=" + describe(definition));
            collisionLogService.recordCollision("loaded:itemType", existingByType, definition, "ignored incoming loaded definition");
            return;
        }

        byItemType.put(definition.itemType(), definition);

        if (definition.requiredMaterial() != null && definition.requiredCustomModelData() != null) {
            String fallbackKey = fallbackKey(definition.requiredMaterial(), definition.requiredCustomModelData());
            CustomItemDefinition existingByFallback = byMaterialAndCmd.get(fallbackKey);
            if (existingByFallback != null && !existingByFallback.itemType().equals(definition.itemType())) {
                logger.warning(() -> "Ignoring loaded conflicting material+CMD fallback '" + fallbackKey
                        + "': existing=" + describe(existingByFallback)
                        + " incoming=" + describe(definition));
                collisionLogService.recordCollision("loaded:material+CMD", existingByFallback, definition, "ignored incoming loaded definition");
                return;
            }
            byMaterialAndCmd.put(fallbackKey, definition);
        }
    }

    private boolean sameIdentity(CustomItemDefinition left, CustomItemDefinition right) {
        return Objects.equals(left.itemType(), right.itemType())
                && left.requiredMaterial() == right.requiredMaterial()
                && Objects.equals(left.requiredCustomModelData(), right.requiredCustomModelData());
    }

    private String fallbackKey(Material material, Float requiredCustomModelData) {
        return material.name() + "|" + Float.toString(requiredCustomModelData);
    }

    private String describe(CustomItemDefinition definition) {
        return definition.itemType() + "[" + definition.requiredMaterial() + "|" + definition.requiredCustomModelData() + "|" + definition.state() + "]";
    }
}
