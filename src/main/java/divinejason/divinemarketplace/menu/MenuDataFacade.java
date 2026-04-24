package divinejason.divinemarketplace.menu;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Collects menu-ready data from underlying stores/services so the renderer does
 * not talk to many repositories directly.
 *
 * This is intentionally only a façade/scaffold right now.
 */
public final class MenuDataFacade {

    public List<CategorySummary> getMainCategories(int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<SubcategorySummary> searchMarketGroups(String query, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<Listing> getListings(String marketKeyOrNull, SortMode sortMode, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<Listing> getPlayerListings(UUID playerUuid, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public Listing getListingById(UUID listingId) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<ItemClaimRecord> getPlayerItemClaims(UUID playerUuid, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public long getPlayerMoneyClaimBalance(UUID playerUuid) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<SaleRecord> getSaleHistory(String marketKey, int page) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    public List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month) {
        throw new UnsupportedOperationException("pseudocode scaffold");
    }
}
