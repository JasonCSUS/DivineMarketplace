package divinejason.divinemarketplace.menu;


/*
 * File role: Adapts marketplace services into page-shaped data that the renderer can consume without business logic.
 */
import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.auction.service.CategoryService;
import divinejason.divinemarketplace.auction.service.HistoryService;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Collects menu-ready read data from stores/services so renderers do not talk to many repositories directly.
 */
public final class MenuDataFacade {
    private final CategoryService categoryService;
    private final SQLiteListingStore listingStore;
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final HistoryService historyService;

    public MenuDataFacade(
            CategoryService categoryService,
            SQLiteListingStore listingStore,
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            HistoryService historyService
    ) {
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
    }

    public MenuPage<CategorySummary> getMainCategoriesPage(int page, int pageSize) {
        return pageFrom(categoryService::getTopLevelCategories, page, pageSize);
    }

    public MenuPage<SubcategorySummary> getMarketGroupsForCategoryPage(String categoryId, int page, int pageSize) {
        if (categoryId == null || categoryId.isBlank()) {
            return MenuPage.of(List.of(), page, false);
        }
        return pageFrom((pageNumber, size) -> categoryService.getMarketGroupsForCategory(categoryId, pageNumber, size), page, pageSize);
    }

    public MenuPage<EnchantBrowseSummary> getEnchantedBookTargetGroupsPage(int page, int pageSize) {
        return pageFrom(categoryService::getEnchantedBookTargetGroups, page, pageSize);
    }

    public MenuPage<SubcategorySummary> getEnchantedBookMarketGroupsPage(EnchantBrowseGroup group, int page, int pageSize) {
        return pageFrom((pageNumber, size) -> categoryService.getEnchantedBookMarketGroups(group, pageNumber, size), page, pageSize);
    }

    public MenuPage<SubcategorySummary> searchMarketGroupsPage(String query, int page, int pageSize) {
        if (query == null || query.isBlank()) {
            return MenuPage.of(List.of(), page, false);
        }
        return pageFrom((pageNumber, size) -> categoryService.searchMarketGroups(query, pageNumber, size), page, pageSize);
    }

    public MenuPage<Listing> getListingsPage(String marketKeyOrNull, SortMode sortMode, int page, int pageSize) {
        SortMode mode = sortMode == null ? SortMode.NEWEST_FIRST : sortMode;
        if (marketKeyOrNull == null || marketKeyOrNull.isBlank()) {
            return pageFrom((pageNumber, size) -> listingStore.findAll(mode, pageNumber, size), page, pageSize);
        }
        return pageFrom((pageNumber, size) -> listingStore.findByMarketKey(marketKeyOrNull, mode, pageNumber, size), page, pageSize);
    }

    public MenuPage<Listing> getPlayerListingsPage(UUID playerUuid, int page, int pageSize) {
        return pageFrom((pageNumber, size) -> listingStore.findBySeller(playerUuid, pageNumber, size), page, pageSize);
    }

    public Listing getListingById(UUID listingId) {
        if (listingId == null) {
            return null;
        }
        return listingStore.findById(listingId).orElse(null);
    }

    public MenuPage<ItemClaimRecord> getPlayerItemClaimsPage(UUID playerUuid, int page, int pageSize) {
        return pageFrom((pageNumber, size) -> itemClaimStore.findByOwner(playerUuid, pageNumber, size), page, pageSize);
    }

    public ItemClaimRecord getPlayerItemClaimById(UUID playerUuid, UUID claimId) {
        if (playerUuid == null || claimId == null) {
            return null;
        }
        return itemClaimStore.findById(claimId, playerUuid).orElse(null);
    }

    public long getPlayerMoneyClaimBalance(UUID playerUuid) {
        return moneyClaimStore.getBalanceOrZero(playerUuid);
    }

    public MenuPage<SaleRecord> getSaleHistoryPage(String marketKey, int page, int pageSize) {
        if (marketKey == null || marketKey.isBlank()) {
            return MenuPage.of(List.of(), page, false);
        }
        return pageFrom((pageNumber, size) -> historyService.getSaleHistory(marketKey, pageNumber, size), page, pageSize);
    }

    public List<YearMonth> getPriceHistoryMonths(String marketKey) {
        if (marketKey == null || marketKey.isBlank()) {
            return List.of();
        }
        return historyService.getPriceHistoryMonths(marketKey);
    }

    public MenuPage<RecommendationHistoryPoint> getPriceHistoryPage(String marketKey, YearMonth month, int page, int pageSize) {
        if (marketKey == null || marketKey.isBlank() || month == null) {
            return MenuPage.of(List.of(), page, false);
        }

        List<RecommendationHistoryPoint> points = historyService.getPriceHistory(marketKey, month);
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        int start = safePage * safePageSize;
        if (start >= points.size()) {
            return MenuPage.of(List.of(), safePage, false);
        }
        int end = Math.min(points.size(), start + safePageSize);
        boolean hasNext = end < points.size();
        return MenuPage.of(points.subList(start, end), safePage, hasNext);
    }

    private <T> MenuPage<T> pageFrom(BiFunction<Integer, Integer, List<T>> loader, int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        List<T> loaded = loader.apply(safePage, safePageSize + 1);
        boolean hasNext = loaded.size() > safePageSize;
        List<T> current = hasNext ? loaded.subList(0, safePageSize) : loaded;
        return MenuPage.of(current, safePage, hasNext);
    }
}
