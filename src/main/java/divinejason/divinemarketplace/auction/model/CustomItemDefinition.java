package divinejason.divinemarketplace.auction.model;

import org.bukkit.Material;

/**
 * Config-backed definition for one known custom item type.
 *
 * Locked v1 definition fields:
 * - itemType
 * - requiredMaterial
 * - requiredCustomModelData
 * - marketDisplayName
 * - categoryId
 *
 * Meaning:
 * - itemType is the primary identity key for custom items
 * - itemType also serves as the marketKey for custom items in v1
 * - requiredMaterial + requiredCustomModelData are used to match and recreate
 *   custom-model-based items when no cleaner plugin-specific id is available
 * - marketDisplayName is the safe canonical grouped/subcategory label
 * - categoryId is the top-level browse category and may initially default to
 *   "unsorted" when auto-discovered
 * - state tracks whether admins have explicitly reviewed the definition
 *
 * Important:
 * - the exact listed item snapshot is still what should be shown in the final
 *   listing preview/purchase UI
 * - this definition is a matching/grouping template, not the full visual item
 *   snapshot itself
 */
public record CustomItemDefinition(
        String itemType,
        Material requiredMaterial,
        Float requiredCustomModelData,
        String marketDisplayName,
        String categoryId,
        CustomItemDefinitionState state
) {
    public CustomItemDefinition {
        if (state == null) {
            state = CustomItemDefinitionState.CONFIRMED;
        }
    }

    public boolean provisional() {
        return state.provisional();
    }

    public CustomItemDefinition confirmed() {
        return new CustomItemDefinition(itemType, requiredMaterial, requiredCustomModelData, marketDisplayName, categoryId, CustomItemDefinitionState.CONFIRMED);
    }
}
