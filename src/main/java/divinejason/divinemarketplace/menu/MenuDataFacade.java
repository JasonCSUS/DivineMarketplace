package divinejason.divinemarketplace.menu;

/*
 * Layer : GUI
 * Owns  : menu-shaped data reads for the renderer and click router
 * Calls : CategoryService, SQLiteListingStore, SQLiteItemClaimStore,
 *         SQLiteMoneyClaimStore, HistoryService, ListingPolicyResolver
 *
 * ALL reads in this class are served from memory-first stores that load their
 * rows at startup.  No SQL queries are issued during normal GUI click handling.
 * The only time SQL is involved is on plugin startup/reload when stores
 * populate their in-memory caches.
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
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.history.HistoryService;
import divinejason.divinemarketplace.auction.service.identity.ListingPolicyResolver;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMoneyClaimStore;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import org.bukkit.entity.Player;

/**
 * Collects menu-ready read data from stores/services so renderers do not talk to many repositories directly.
 */
public final class MenuDataFacade {
    private final CategoryService categoryService;
    private final SQLiteListingStore listingStore;
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final HistoryService historyService;
    private final ListingPolicyResolver listingPolicyResolver;

    public MenuDataFacade(
            CategoryService categoryService,
            SQLiteListingStore listingStore,
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            HistoryService historyService,
            ListingPolicyResolver listingPolicyResolver
    ) {
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.listingPolicyResolver = Objects.requireNonNull(listingPolicyResolver, "listingPolicyResolver");
    }

    public MenuPage<CategorySummary> getMainCategoriesPage(int page, int pageSize) {
        return pageFrom(categoryService::getTopLevelCategories, page, pageSize);
    }

    public String getCategoryDisplayName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return "Market Category";
        }

        String normalized = categoryId.trim().toLowerCase(Locale.ROOT);
        return categoryService.getTopLevelCategories(0, 512).stream()
                .filter(category -> category.categoryId().equalsIgnoreCase(normalized))
                .map(CategorySummary::displayName)
                .findFirst()
                .orElseGet(() -> prettyName(categoryId));
    }

    public String getMarketDisplayName(String marketKey) {
        if (marketKey == null || marketKey.isBlank()) {
            return "All Listings";
        }

        return listingStore.findActiveByMarketKeyUnsorted(marketKey).stream()
                .findFirst()
                .map(Listing::marketDisplayName)
                .or(() -> historyService.getSaleHistory(marketKey, 0, 1).stream()
                        .findFirst()
                        .map(SaleRecord::marketDisplayName))
                .orElseGet(() -> prettyName(marketKey));
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

    public List<Listing> getPlayerListings(UUID playerUuid, int page, int pageSize) {
        if (playerUuid == null) {
            return List.of();
        }
        return listingStore.findBySeller(playerUuid, Math.max(0, page), Math.max(1, pageSize));
    }

    public int getPlayerListingCount(UUID playerUuid) {
        if (playerUuid == null) {
            return 0;
        }
        return listingStore.countBySeller(playerUuid);
    }

    public int getPlayerListingCapacity(Player player) {
        if (player == null) {
            return 0;
        }
        return Math.max(0, listingPolicyResolver.resolve(player).maxListings());
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
        List<T> current = loader.apply(safePage, safePageSize);
        boolean hasNext = current.size() >= safePageSize && !loader.apply(safePage + 1, safePageSize).isEmpty();
        return MenuPage.of(current, safePage, hasNext);
    }

    private String prettyName(String token) {
        if (token == null || token.isBlank()) {
            return "Unknown";
        }
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_:| -]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? "Unknown" : builder.toString();
    }
}
