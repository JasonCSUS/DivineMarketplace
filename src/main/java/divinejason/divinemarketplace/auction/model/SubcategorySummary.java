package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable subcategory summary data between marketplace services, persistence stores, commands, and GUI rendering.
 */
/**
 * Runtime dynamic subcategory entry.
 *
 * Important:
 * - listedQuantity represents total listed item quantity for the market group
 * - it is intentionally quantity-oriented market flow data, not number of listing records
 *
 * previewIconKey is a UI hint only; the menu layer builds the actual display item.
 */
public record SubcategorySummary(
        String marketKey,
        String marketDisplayName,
        String previewIconKey,
        int listedQuantity
) {
}
