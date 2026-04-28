package divinejason.divinemarketplace.auction.model;

/**
 * Runtime top-level category summary.
 *
 * Important:
 * - activeListingCount represents the number of active listing records
 * - it is not the total stacked item quantity
 * - subcategory summaries carry quantity-oriented listed amount data
 */
public record CategorySummary(
        String categoryId,
        int activeListingCount
) {
}
