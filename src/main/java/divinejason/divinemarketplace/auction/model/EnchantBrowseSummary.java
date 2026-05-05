package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable enchant browse summary data between marketplace services, persistence stores, commands, and GUI rendering.
 */
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
