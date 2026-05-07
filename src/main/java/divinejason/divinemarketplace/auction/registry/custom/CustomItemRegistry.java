package divinejason.divinemarketplace.auction.registry.custom;


/*
 * File role: Defines the service contract for custom item registry so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import org.bukkit.Material;

/**
 * Registry of configured custom item definitions.
 *
 * Responsibilities:
 * - load custom item definitions from the configured data source
 * - build and cache in-memory lookup maps
 * - provide fast lookup by item_type and material/custom-model-data fallback
 * - support write-through updates when auto-discovery or admin sorting defines items
 */
public interface CustomItemRegistry {
    CustomItemDefinition findByItemType(String itemType);

    CustomItemDefinition findByMaterialAndCustomModelData(Material material, Float requiredCustomModelData);

    CustomItemDefinition upsertDefinition(CustomItemDefinition definition);

    void reload();
}
