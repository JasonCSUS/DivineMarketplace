package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;

/**
 * Registry of configured custom item definitions.
 *
 * Responsibilities:
 * - load custom item definitions from the configured data source
 *   (yaml / csv / sql / etc.)
 * - build and cache an in-memory map: item_type -> CustomItemDefinition
 * - provide fast runtime lookup by item_type
 * - support reload
 * - support write-through updates when admin commands define or re-sort items
 *
 * Non-responsibilities:
 * - does not inspect ItemStacks directly
 * - does not extract custom ids from item metadata
 * - does not build ResolvedItemDefinition
 *
 * Current custom item definition shape:
 * - itemType
 * - requiredMaterial
 * - requiredCustomModelData
 * - marketDisplayName
 * - categoryId
 */
public interface CustomItemRegistry {
    CustomItemDefinition findByItemType(String itemType);
}

/*
registry flow:

startup / reload:
- call CustomItemDataSource to load configured custom item definitions
- build in-memory map:
    itemType -> CustomItemDefinition

runtime:
- ItemIdentityResolver extracts itemType if available
- registry does fast in-memory lookup
- if no itemType is available, resolver may still auto-discover a provisional
  definition using material + custom model data and write it through here
*/
