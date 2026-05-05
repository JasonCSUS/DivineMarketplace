package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for market icon resolver so command, GUI, and runtime code share one behavior boundary.
 */
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
 * - custom UI elements and top-level category icons use configured material/CMD icons today
 * - external custom icon ids are preserved as resolver input so integrations can map them when supported
 */
public interface MarketIconResolver {
    ItemStack resolveCategoryIcon(CategoryDefinition categoryDefinition);

    ItemStack resolvePreviewIcon(ResolvedItemDefinition resolvedItemDefinition);
}
