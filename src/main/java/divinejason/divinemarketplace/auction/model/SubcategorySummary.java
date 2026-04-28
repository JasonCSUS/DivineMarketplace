package divinejason.divinemarketplace.auction.model;

/**
 * Runtime dynamic subcategory entry.
 *
 * Important:
 * - activeListingCount represents total listed item quantity for the market group
 * - it is intentionally quantity-oriented market flow data, not number of listing records
 *
 * previewIconKey is a UI hint only; the actual icon is built later by MarketIconResolver.
 */
public record SubcategorySummary(
        String marketKey,
        String marketDisplayName,
        String previewIconKey,
        int activeListingCount
) {
}
