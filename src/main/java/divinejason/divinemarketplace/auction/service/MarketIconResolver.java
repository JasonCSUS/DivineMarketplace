package divinejason.divinemarketplace.auction.service;

import org.bukkit.inventory.ItemStack;

import divinejason.divinemarketplace.auction.model.CategoryDefinition;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;

/**
 * Resolves menu icons for either vanilla or custom-backed UI elements.
 *
 * Current direction:
 * - top-level category icons come from config icon keys
 * - dynamic subcategory icons should prefer the discovered/cloned item preview
 *   when available so new custom items still render correctly without manual
 *   icon definition
 * - custom UI elements and top-level category icons may later use ItemAdder ids
 *   or other custom icon identifiers
 */
public interface MarketIconResolver {
    ItemStack resolveCategoryIcon(CategoryDefinition categoryDefinition);

    ItemStack resolvePreviewIcon(ResolvedItemDefinition resolvedItemDefinition);
}
