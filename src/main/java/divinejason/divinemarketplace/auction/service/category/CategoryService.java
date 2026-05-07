package divinejason.divinemarketplace.auction.service.category;


/*
 * File role: Defines the service contract for category service so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import java.util.Collection;
import java.util.List;

/**
 * Category and browse indexing service.
 *
 * Locked browse rules:
 * - top-level categories are always shown, even at count 0
 * - subcategories/market groups appear only when active listings exist
 * - All Listings is separate from categories and defaults to newest-first
 */
public interface CategoryService {

    List<CategorySummary> getTopLevelCategories(int page, int pageSize);

    String getCategoryDisplayName(String categoryId);

    List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page, int pageSize);

    List<SubcategorySummary> searchMarketGroups(String query, int page, int pageSize);

    List<EnchantBrowseSummary> getEnchantedBookTargetGroups(int page, int pageSize);

    List<SubcategorySummary> getEnchantedBookMarketGroups(EnchantBrowseGroup group, int page, int pageSize);

    /**
     * Refresh browse indexes after listing create/update/cancel/expire/sale.
     * Implementations should rebuild no more than the affected category when possible.
     */
    void refreshIndexesFor(String marketKey, String categoryId);

    /**
     * Batched variant for flows such as hourly expiration that may affect many listings.
     */
    default void refreshIndexesForCategories(Collection<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (String categoryId : categoryIds) {
            refreshIndexesFor(null, categoryId);
        }
    }

    /**
     * Full rebuild fallback if indexes ever need to be reconstructed from active listings.
     */
    void rebuildAllIndexes();
}
