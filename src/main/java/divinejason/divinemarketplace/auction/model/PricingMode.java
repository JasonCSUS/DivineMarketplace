package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates pricing mode values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Determines how pricing / recommendation logic should treat an item family.
 *
 * COMMODITY
 * - normal vanilla items
 * - equipment grouped by base material while ignoring enchants
 *
 * EXACT_VARIANT
 * - potion variants
 * - single-enchant books
 *
 * CUSTOM_ITEM
 * - accessories, keys, and other configured ItemAdder id or material + custom model data items
 *
 * PACKAGE_NO_RECOMMENDATION
 * - filled shulkers/packages
 * - previewable and tradable, but never train recommendation data
 *
 * LOW_CONFIDENCE_UNIQUE
 * - unknown custom items
 * - mixed books if we keep them as display-only/proxy-priced entries
 */
public enum PricingMode {
    COMMODITY,
    EXACT_VARIANT,
    CUSTOM_ITEM,
    PACKAGE_NO_RECOMMENDATION,
    LOW_CONFIDENCE_UNIQUE
}
