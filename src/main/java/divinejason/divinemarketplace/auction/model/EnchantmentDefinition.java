package divinejason.divinemarketplace.auction.model;

import java.util.Set;

/**
 * Browse metadata for one enchantment.
 *
 * This is about UI grouping and human-readable naming, not direct pricing logic.
 *
 * Current intended sources:
 * - builtin vanilla mapping
 * - local plugin config override
 * - recognized external enchant plugin adapters (for example ExcellentEnchants)
 * - fallback unresolved/unknown behavior
 *
 * Notes:
 * - non-minecraft namespaces should be treated as custom enchants
 * - for ExcellentEnchants, supported-item applicability is more useful for
 *   browse grouping than plugin-specific primary-item metadata
 */
public record EnchantmentDefinition(
        String namespacedKey,
        String displayName,
        Set<EnchantBrowseGroup> browseGroups,
        boolean recognized,
        boolean customEnchantment,
        String sourceHint
) {
}
