package divinejason.divinemarketplace.auction.service;

import org.bukkit.inventory.ItemStack;

import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;

/**
 * Resolves the final market-facing identity from an ItemStack.
 *
 * Contract of resolve():
 * - every successful resolve(...) call must return a fully-populated
 *   ResolvedItemDefinition
 * - the rest of the plugin should not need to re-check category placement,
 *   grouped/subcategory label, recommendation flags, history/training flags,
 *   browse visibility, or review severity
 * - this resolver should be the single source of truth for market-facing item
 *   identity at listing time
 *
 * Locked custom-item direction:
 * - prefer a plugin-provided custom item id when readable
 * - otherwise fall back to material + custom model data
 * - newly discovered custom items should be auto-defined with:
 *   itemType, requiredMaterial, requiredCustomModelData, marketDisplayName,
 *   categoryId = unsorted
 * - all newly discovered custom items should be flagged for NORMAL review
 * - unsafe/ambiguous items should remain listable but become RECENT_ONLY and
 *   HIGH_PRIORITY review
 *
 * Locked participation direction:
 * - admin transaction history is mandatory and not represented as a flag here
 * - player-facing market history and market training are separate flags
 * - mixed enchanted books should usually be INCLUDED in market history but
 *   EXCLUDED from market training
 * - packages/shulkers/bundles should generally be EXCLUDED from both market
 *   history and market training
 *
 * Locked recommendation direction:
 * - if recommendationEnabled is false, no recommendation is shown
 * - if the item is an enchanted book, use the highest-priced enchant in the map
 * - otherwise use the recommendation for the resolved marketKey
 */
public interface ItemIdentityResolver {
    ResolvedItemDefinition resolve(ItemStack itemStack);
}

/*
Custom item resolution sketch:

1. Try to extract a stable custom item identity.
2. If a known definition exists, build the resolved custom item result.
3. If only material + custom model data is available, auto-create a provisional
   definition and default categoryId to unsorted.
4. If the item still cannot be safely grouped, return a RECENT_ONLY /
   HIGH_PRIORITY result so the item remains listable but does not pollute sorted
   browsing.
*/
