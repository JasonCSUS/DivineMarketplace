package divinejason.divinemarketplace.auction.model;

/**
 * Runtime top-level category summary.
 *
 * This is separate from CategoryDefinition so the config stays simple while the
 * UI can attach live listing counts as lore.
 */
public record CategorySummary(
        String categoryId,
        int activeListingCount
) {
}
