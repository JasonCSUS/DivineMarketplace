package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates item class values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Broad routing classification used before resolving full market identity.
 *
 * Keep this enum coarse. It is only the entry point into the resolver.
 * We do NOT want dozens of enum values here for every final category.
 *
 * Flow:
 * 1. classifier decides which special branch the item belongs to
 * 2. resolver switches on ItemClass
 * 3. resolver builds final market key/category/pricing behavior
 *
 * Notes:
 * - equipment is intentionally not its own class anymore.
 *   Weapons/armor/tools now go through DEFAULT and are handled inside the
 *   default resolution path.
 * - custom items are routed broadly as CUSTOM_DATA_ITEM first.
 *   Known vs unknown custom items are decided during ItemIdentityResolver resolution.
 */
public enum ItemClass {
    CUSTOM_DATA_ITEM,
    PACKAGE,
    ENCHANTED_BOOK,
    POTION_LIKE,
    DEFAULT
}
