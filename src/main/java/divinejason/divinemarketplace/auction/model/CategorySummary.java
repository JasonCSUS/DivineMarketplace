package divinejason.divinemarketplace.auction.model;

/**
 * Runtime top-level category summary.
 *
 * activeListingCount is the number of active listing records in the category,
 * not the total stacked item quantity. Display data is copied from
 * category_config.yml so player-facing commands and GUI items do not expose raw
 * category ids unless the server owner intentionally uses ids as names.
 */
public record CategorySummary(
        String categoryId,
        String displayName,
        String iconKey,
        int activeListingCount
) {
}
