package divinejason.divinemarketplace.auction.model;

/**
 * Runtime dynamic subcategory entry.
 *
 * Subcategories are generated only from active listings and should never render
 * large pages of empty entries. previewIconKey is a UI hint only; the actual icon
 * is built later by MarketIconResolver.
 */
public record SubcategorySummary(
        String marketKey,
        String marketDisplayName,
        String previewIconKey,
        int activeListingCount
) {
}
