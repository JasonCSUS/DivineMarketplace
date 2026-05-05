package divinejason.divinemarketplace.auction.model;

/**
 * First-layer enchanted-book browse summary.
 *
 * Example:
 * - /market enchanted_books -> Sword, Armor, Tools, Universal
 * - /market enchanted_books sword -> actual active book market groups
 */
public record EnchantBrowseSummary(
        EnchantBrowseGroup group,
        String displayName,
        int activeMarketGroupCount,
        int listedQuantity
) {
}
