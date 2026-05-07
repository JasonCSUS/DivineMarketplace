package divinejason.divinemarketplace.menu;

/*
 * Layer : gui/model
 * Owns  : Pure page-data assembly from memory-first menu data.
 * Calls : MenuDataFacade only.
 * Avoids: Bukkit Inventory creation and ItemStack rendering.
 * Threading: globally cacheable views are async-prep friendly. Player-specific
 *            views may read Player permissions/capacity and should stay on the
 *            main thread until those inputs are snapped into plain data.
 */

import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.menu.model.PageModel;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.Player;

/**
 * Reads from in-memory stores via {@link MenuDataFacade} and produces a {@link PageModel}
 * for the given session.  No Bukkit inventory/ItemStack operations are performed.
 * Cacheable market/category/history views are the intended async-preparation
 * target; player-specific views still receive a live Player until those inputs
 * are snapped into plain request data.
 *
 * <p>One shared instance is created at startup.  The facade it wraps never changes
 * on reload, so no re-construction is needed when the visual config changes.</p>
 */
public final class PageModelBuilder {

    private final MenuDataFacade dataFacade;

    public PageModelBuilder(MenuDataFacade dataFacade) {
        this.dataFacade = Objects.requireNonNull(dataFacade, "dataFacade");
    }

    /**
     * Returns the cache key that uniquely identifies this session's view + data state.
     * Used by {@link MenuController} for both the inventory-holder label and the
     * page-model cache lookup key.
     */
    public static String cacheKey(MenuSession session) {
        return session.currentView().name().toLowerCase(Locale.ROOT)
                + ":p="  + session.page()
                + ":sort=" + (session.sortMode() == null ? "" : session.sortMode().name())
                + ":q="  + nvl(session.searchQuery())
                + ":cat=" + nvl(session.selectedCategoryId())
                + ":enc=" + nvl(session.selectedEnchantGroup())
                + ":mkt=" + nvl(session.selectedMarketKey())
                + ":lst=" + (session.selectedListingId()   == null ? "" : session.selectedListingId())
                + ":clm=" + (session.selectedClaimId()     == null ? "" : session.selectedClaimId())
                + ":pm="  + (session.selectedPriceHistoryMonth() == null ? "" : session.selectedPriceHistoryMonth());
    }

    /**
     * Builds the page model for {@code session}.
     *
     * @param player   current player (may be used for player-specific reads)
     * @param session  current GUI state
     * @param pageSize number of data items per page (from the view's {@link MenuTemplate})
     */
    public PageModel build(Player player, MenuSession session, int pageSize) {
        int ps = Math.max(1, pageSize);
        return switch (session.currentView()) {
            case MAIN             -> buildMain(player, session, ps);
            case CATEGORY_BROWSER -> buildCategoryBrowser(session, ps);
            case SEARCH_RESULTS   -> buildSearchResults(session, ps);
            case LISTING_BROWSER  -> buildListingBrowser(session, ps);
            case ITEM_DETAIL      -> buildItemDetail(player, session);
            case MY_LISTINGS      -> buildMyListings(player, session, ps);
            case CLAIMS           -> buildClaims(player, session, ps);
            case CLAIM_DETAIL     -> buildClaimDetail(player, session);
            case SALE_HISTORY     -> buildSaleHistory(session, ps);
            case PRICE_HISTORY    -> buildPriceHistory(session, ps);
        };
    }

    /**
     * Builds only globally cacheable page models. This method is safe for async
     * menu-page preparation because it never touches Player, Inventory, ItemStack,
     * or permission APIs.
     */
    public PageModel buildCacheable(MenuSession session, int pageSize) {
        int ps = Math.max(1, pageSize);
        return switch (session.currentView()) {
            case CATEGORY_BROWSER -> buildCategoryBrowser(session, ps);
            case SEARCH_RESULTS   -> buildSearchResults(session, ps);
            case LISTING_BROWSER  -> buildListingBrowser(session, ps);
            case SALE_HISTORY     -> buildSaleHistory(session, ps);
            case PRICE_HISTORY    -> buildPriceHistory(session, ps);
            default -> throw new IllegalArgumentException("Menu view is not async-cacheable: " + session.currentView());
        };
    }

    // -------------------------------------------------------------------------
    // Per-view builders
    // -------------------------------------------------------------------------

    private PageModel.Main buildMain(Player player, MenuSession session, int pageSize) {
        MenuPage<divinejason.divinemarketplace.auction.model.CategorySummary> page =
                dataFacade.getMainCategoriesPage(session.page(), pageSize);
        long balance = dataFacade.getPlayerMoneyClaimBalance(player.getUniqueId());
        return new PageModel.Main(page.items(), session.page(), page.hasPrevious(), page.hasNext(), balance);
    }

    private PageModel.CategoryBrowser buildCategoryBrowser(MenuSession session, int pageSize) {
        String categoryId    = session.selectedCategoryId();
        String enchantGroup  = session.selectedEnchantGroup();
        String displayName   = dataFacade.getCategoryDisplayName(categoryId);

        if ("enchanted_books".equalsIgnoreCase(categoryId)) {
            if (enchantGroup == null || enchantGroup.isBlank()) {
                // Top-level: show enchant target groups
                MenuPage<divinejason.divinemarketplace.auction.model.EnchantBrowseSummary> page =
                        dataFacade.getEnchantedBookTargetGroupsPage(session.page(), pageSize);
                return new PageModel.CategoryBrowser(categoryId, enchantGroup, displayName,
                        session.page(), page.hasPrevious(), page.hasNext(), null, page.items());
            } else {
                // Sub-level: show market groups for this enchant group
                EnchantBrowseGroup browseGroup = EnchantBrowseGroup.fromCommandToken(enchantGroup);
                String subDisplay = browseGroup.displayName() + " Enchants";
                MenuPage<SubcategorySummary> page =
                        dataFacade.getEnchantedBookMarketGroupsPage(browseGroup, session.page(), pageSize);
                return new PageModel.CategoryBrowser(categoryId, enchantGroup, subDisplay,
                        session.page(), page.hasPrevious(), page.hasNext(), page.items(), null);
            }
        }

        MenuPage<SubcategorySummary> page =
                dataFacade.getMarketGroupsForCategoryPage(categoryId, session.page(), pageSize);
        return new PageModel.CategoryBrowser(categoryId, enchantGroup, displayName,
                session.page(), page.hasPrevious(), page.hasNext(), page.items(), null);
    }

    private PageModel.SearchResults buildSearchResults(MenuSession session, int pageSize) {
        String query = session.searchQuery();
        MenuPage<SubcategorySummary> page = dataFacade.searchMarketGroupsPage(query, session.page(), pageSize);
        return new PageModel.SearchResults(query, session.page(), page.hasPrevious(), page.hasNext(), page.items());
    }

    private PageModel.ListingBrowser buildListingBrowser(MenuSession session, int pageSize) {
        String marketKey    = session.selectedMarketKey();
        String displayName  = dataFacade.getMarketDisplayName(marketKey);
        MenuPage<Listing> page =
                dataFacade.getListingsPage(marketKey, session.sortMode(), session.page(), pageSize);
        return new PageModel.ListingBrowser(marketKey, session.sortMode(), displayName,
                session.page(), page.hasPrevious(), page.hasNext(), page.items());
    }

    private PageModel.ItemDetail buildItemDetail(Player player, MenuSession session) {
        Listing listing = dataFacade.getListingById(session.selectedListingId());
        if (listing == null) {
            return new PageModel.ItemDetail(null, false, false, 1);
        }
        boolean ownsListing    = player.getUniqueId().equals(listing.sellerUuid());
        boolean mayAdminCancel = player.hasPermission("divinemarketplace.admin")
                              || player.hasPermission("divinemarketplace.admin.listing.cancel");
        boolean canBuy    = !ownsListing;
        boolean canCancel = ownsListing || mayAdminCancel;
        int quantity = Math.max(1, Math.min(session.selectedQuantity(), listing.amount()));
        return new PageModel.ItemDetail(listing, canBuy, canCancel, quantity);
    }

    private PageModel.MyListings buildMyListings(Player player, MenuSession session, int pageSize) {
        int maxSlots    = dataFacade.getPlayerListingCapacity(player);
        int listedCount = dataFacade.getPlayerListingCount(player.getUniqueId());
        int pageStart   = session.page() * pageSize;
        int visible     = Math.max(0, Math.min(pageSize, maxSlots - pageStart));
        List<Listing> listings = dataFacade.getPlayerListings(player.getUniqueId(), session.page(), pageSize);
        boolean hasPrev = session.page() > 0;
        boolean hasNext = pageStart + pageSize < maxSlots;
        return new PageModel.MyListings(session.page(), hasPrev, hasNext, listings, maxSlots, listedCount, visible);
    }

    private PageModel.ClaimsPage buildClaims(Player player, MenuSession session, int pageSize) {
        MenuPage<ItemClaimRecord> page =
                dataFacade.getPlayerItemClaimsPage(player.getUniqueId(), session.page(), pageSize);
        long balance = dataFacade.getPlayerMoneyClaimBalance(player.getUniqueId());
        return new PageModel.ClaimsPage(session.page(), page.hasPrevious(), page.hasNext(), page.items(), balance);
    }

    private PageModel.ClaimDetail buildClaimDetail(Player player, MenuSession session) {
        ItemClaimRecord claim = dataFacade.getPlayerItemClaimById(player.getUniqueId(), session.selectedClaimId());
        return new PageModel.ClaimDetail(claim);
    }

    private PageModel.SaleHistory buildSaleHistory(MenuSession session, int pageSize) {
        String marketKey   = session.selectedMarketKey();
        String displayName = dataFacade.getMarketDisplayName(marketKey);
        MenuPage<SaleRecord> page = dataFacade.getSaleHistoryPage(marketKey, session.page(), pageSize);
        return new PageModel.SaleHistory(marketKey, displayName,
                session.page(), page.hasPrevious(), page.hasNext(), page.items());
    }

    private PageModel.PriceHistory buildPriceHistory(MenuSession session, int pageSize) {
        String marketKey   = session.selectedMarketKey();
        String displayName = dataFacade.getMarketDisplayName(marketKey);
        List<YearMonth> months = dataFacade.getPriceHistoryMonths(marketKey);
        if (months.isEmpty()) {
            return new PageModel.PriceHistory(marketKey, displayName, null, 0, 0,
                    session.page(), false, false, List.of());
        }
        YearMonth selected = session.selectedPriceHistoryMonth();
        if (selected == null || !months.contains(selected)) {
            selected = months.get(0);
        }
        int monthIndex = months.indexOf(selected);
        MenuPage<RecommendationHistoryPoint> page =
                dataFacade.getPriceHistoryPage(marketKey, selected, session.page(), pageSize);
        return new PageModel.PriceHistory(marketKey, displayName, selected, monthIndex, months.size(),
                session.page(), page.hasPrevious(), page.hasNext(), page.items());
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
