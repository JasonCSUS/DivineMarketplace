package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;

import java.util.List;

/**
 * Temporary low-risk category service placeholder.
 *
 * Current role:
 * - keep service wiring clean while the real browse/index implementation is still pending
 * - accept refresh/rebuild calls without exploding runtime service bootstrap
 */
public final class DefaultCategoryService implements CategoryService {
    @Override
    public List<CategorySummary> getTopLevelCategories(int page, int pageSize) {
        return List.of();
    }

    @Override
    public List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page, int pageSize) {
        return List.of();
    }

    @Override
    public List<SubcategorySummary> searchMarketGroups(String query, int page, int pageSize) {
        return List.of();
    }

    @Override
    public void refreshIndexesFor(String marketKey, String categoryId) {
        // real indexing will be added later
    }

    @Override
    public void rebuildAllIndexes() {
        // real indexing will be added later
    }
}
