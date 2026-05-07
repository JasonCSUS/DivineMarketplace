package divinejason.divinemarketplace.menu.model;

/*
 * File role: Pure-Java data models for every menu view — no Bukkit objects, safe to build on async threads.
 */

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import java.time.YearMonth;
import java.util.List;

/**
 * Sealed hierarchy of per-view data snapshots.  Each record carries exactly the
 * data the renderer needs to build ItemStacks — no Bukkit types, no store calls.
 *
 * <p>Cacheable models (CATEGORY_BROWSER, SEARCH_RESULTS, LISTING_BROWSER,
 * SALE_HISTORY, PRICE_HISTORY) share instances across players.  All other models
 * may include player-specific fields and must not be shared.</p>
 */
public sealed interface PageModel {

    // -------------------------------------------------------------------------
    // Shared / cacheable models
    // -------------------------------------------------------------------------

    /**
     * CATEGORY_BROWSER — regular groups, enchant-target list, or enchant sub-groups.
     * Exactly one of {@code groups} or {@code enchantTargets} is non-null.
     */
    record CategoryBrowser(
            String selectedCategoryId,
            String selectedEnchantGroup,
            String categoryDisplayName,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            /** Non-null for regular categories and enchant sub-group mode. */
            List<SubcategorySummary> groups,
            /** Non-null only for enchanted_books top-level (no enchant group selected). */
            List<EnchantBrowseSummary> enchantTargets
    ) implements PageModel {}

    record SearchResults(
            String searchQuery,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<SubcategorySummary> groups
    ) implements PageModel {}

    record ListingBrowser(
            String selectedMarketKey,
            SortMode sortMode,
            String marketDisplayName,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<Listing> listings
    ) implements PageModel {}

    record SaleHistory(
            String selectedMarketKey,
            String marketDisplayName,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<SaleRecord> sales
    ) implements PageModel {}

    record PriceHistory(
            String selectedMarketKey,
            String marketDisplayName,
            YearMonth selectedMonth,
            int monthIndex,
            int totalMonths,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<RecommendationHistoryPoint> points
    ) implements PageModel {}

    // -------------------------------------------------------------------------
    // Player-specific models (not cached across players)
    // -------------------------------------------------------------------------

    record Main(
            List<CategorySummary> categories,
            int page,
            boolean hasPrevious,
            boolean hasNext,
            long playerBalanceHundredths
    ) implements PageModel {}

    record ItemDetail(
            Listing listing,
            boolean canBuy,
            boolean canCancel,
            int selectedQuantity
    ) implements PageModel {}

    record MyListings(
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<Listing> listings,
            int maxListingSlots,
            int listedCount,
            int visibleListingSlots
    ) implements PageModel {}

    record ClaimsPage(
            int page,
            boolean hasPrevious,
            boolean hasNext,
            List<ItemClaimRecord> claims,
            long playerBalanceHundredths
    ) implements PageModel {}

    record ClaimDetail(
            ItemClaimRecord claim
    ) implements PageModel {}
}
