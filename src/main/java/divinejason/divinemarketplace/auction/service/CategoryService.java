package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;

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

    List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page, int pageSize);

    List<SubcategorySummary> searchMarketGroups(String query, int page, int pageSize);

    /**
     * Refresh indexes after listing create/update/cancel/expire/sale.
     */
    void refreshIndexesFor(String marketKey, String categoryId);

    /**
     * Full rebuild fallback if indexes ever need to be reconstructed from active listings.
     */
    void rebuildAllIndexes();
}
