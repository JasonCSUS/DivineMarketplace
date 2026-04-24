package divinejason.divinemarketplace.auction.model;

import java.util.Map;

/**
 * Final market-facing interpretation of an item at listing time.
 *
 * Locked v1 shape:
 *
 * Core resolved identity
 * - marketKey: stable internal grouping identity
 * - marketDisplayName: safe grouped/subcategory label shown to players
 * - categoryId: top-level browse category
 *
 * Custom-item-specific
 * - itemType: nullable, mainly meaningful for custom items
 *
 * Market behavior
 * - recommendationEnabled: whether a displayed recommendation should exist
 * - marketHistoryParticipation: whether it appears in player-facing market history
 * - marketTrainingParticipation: whether it feeds recommendation training
 *
 * UI/admin behavior
 * - browseVisibility: FULLY_SORTED or RECENT_ONLY
 * - reviewFlagLevel: NONE / NORMAL / HIGH_PRIORITY
 *
 * Book-specific optional data
 * - enchantments: enough data to sort books and derive recommendation by using
 *   the highest-priced enchant in the map
 *
 * Notes:
 * - marketDisplayName is effectively the dynamic subcategory label
 * - marketKey is intentionally separate from marketDisplayName
 * - itemClass is not stored here; it is a resolver-internal branching aid
 * - admin transaction history is mandatory system behavior and should not be
 *   represented here as a participation flag
 */
public record ResolvedItemDefinition(
        String marketKey,
        String marketDisplayName,
        String categoryId,
        String itemType,
        boolean recommendationEnabled,
        MarketHistoryParticipation marketHistoryParticipation,
        MarketTrainingParticipation marketTrainingParticipation,
        BrowseVisibility browseVisibility,
        ReviewFlagLevel reviewFlagLevel,
        Map<String, Integer> enchantments
) {
}
