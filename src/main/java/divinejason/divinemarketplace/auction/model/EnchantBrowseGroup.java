package divinejason.divinemarketplace.auction.model;

/**
 * UI-only browse targets for enchanted books.
 *
 * This is NOT market identity.
 * A book can belong to multiple groups at once.
 *
 * Example:
 * - Sharpness V + Protection IV appears in both SWORD and ARMOR browse views
 * - It is still one listing with one market/display identity
 */
public enum EnchantBrowseGroup {
    SWORD,
    AXE,
    BOW,
    CROSSBOW,
    TRIDENT,
    ARMOR,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    TOOLS,
    PICKAXE,
    SHOVEL,
    HOE,
    UNIVERSAL,
    UNKNOWN
}
