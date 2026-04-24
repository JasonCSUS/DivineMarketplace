package divinejason.divinemarketplace.auction.service;

import org.bukkit.inventory.ItemStack;

/**
 * Extracts the best available custom-item identity signal from a live item.
 *
 * Current priority direction:
 * 1. ItemAdder/custom item id, if readable through plugin API/helpers
 * 2. fallback matching helpers based on material + custom model data
 *
 * Important:
 * - this extractor should not decide category placement on its own
 * - it should only expose identity hints that ItemIdentityResolver can use
 * - if the clean id path is not available, the resolver may still auto-discover
 *   the item using material + custom model data and create a provisional custom
 *   definition in category "unsorted"
 */
public interface CustomItemTypeExtractor {
    /**
     * TODO:
     * - return a stable custom item type/id if one can be read directly
     * - return null if only fallback material/model-data matching is available
     */
    String extractItemType(ItemStack itemStack);
}
