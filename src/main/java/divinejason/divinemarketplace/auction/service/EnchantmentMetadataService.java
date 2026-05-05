package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for enchantment metadata service so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.EnchantmentDefinition;

/**
 * Provides browse metadata for enchantments.
 *
 * This is intentionally about "works on" browse organization and display naming,
 * not direct pricing identity.
 *
 * Planned lookup order:
 * 1. local config override
 * 2. known external enchant plugin adapter
 *    - current known target: ExcellentEnchants
 * 3. builtin vanilla mapping
 * 4. unresolved fallback definition routed into unsorted browse behavior
 *
 * Mixed/custom-enchant notes:
 * - mixed books should appear in every recognized browse group they belong to
 * - if any component enchant is unresolved, the book should also appear in an
 *   unsorted enchanted-book browse bucket
 */
public interface EnchantmentMetadataService {
    EnchantmentDefinition resolveDefinition(String namespacedEnchantKey);
}
