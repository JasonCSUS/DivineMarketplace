package divinejason.divinemarketplace.menu;

/*
 * Layer : gui/render
 * Owns  : Bukkit Inventory rendering from already-prepared PageModel data.
 * Calls : MenuItemFactory, MenuTemplateRegistry, MenuPageCache, PageModelBuilder, menu/template/*.
 * Avoids: market mutations, SQL, purchase/claim/listing business rules.
 * Threading: render(...) must run on the Bukkit main thread because it creates
 *            Inventories and ItemStacks. Cacheable PageModel data is plain Java.
 */

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.menu.model.PageModel;
import divinejason.divinemarketplace.menu.template.AdminControlsTemplate;
import divinejason.divinemarketplace.menu.template.BorderTemplate;
import divinejason.divinemarketplace.menu.template.ConfirmCancelTemplate;
import divinejason.divinemarketplace.menu.template.MenuRenderContext;
import divinejason.divinemarketplace.menu.template.MenuSubTemplate;
import divinejason.divinemarketplace.menu.template.PaginationFooterTemplate;
import divinejason.divinemarketplace.menu.template.PlayerSummaryHeaderTemplate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Combines a full-screen template, cached static chrome, and a PageModel into
 * one Bukkit inventory plus a slot-action map.
 *
 * <p>Common chrome (border fill, back button, pagination arrows, earnings header) is
 * applied via {@link divinejason.divinemarketplace.menu.template.MenuSubTemplate} chunks at
 * the top of each render method.  The expensive data-selection step is isolated in
 * {@link PageModelBuilder} and cacheable models are stored in {@link MenuPageCache}.</p>
 */
public final class MenuRenderer {
    private static final int DEFAULT_SIZE = 54;

    private final MenuDataFacade dataFacade;
    private final MenuVisualConfig visuals;
    private final MenuDataVersion dataVersion;
    private final MenuPageCache pageCache;
    private final MenuTemplateRegistry templates;
    private final PageModelBuilder pageModelBuilder;
    private final MenuRenderContext ctx;

    public MenuRenderer(MenuDataFacade dataFacade,
                        MenuItemFactory itemFactory,
                        MenuVisualConfig visuals,
                        MenuDataVersion dataVersion,
                        MenuPageCache pageCache,
                        PageModelBuilder pageModelBuilder) {
        this.dataFacade       = dataFacade;
        this.visuals          = visuals;
        this.dataVersion      = dataVersion;
        this.pageCache        = pageCache;
        this.templates        = new MenuTemplateRegistry(visuals);
        this.pageModelBuilder = pageModelBuilder;
        this.ctx = new MenuRenderContext(new MenuStaticButtonCache(itemFactory), itemFactory, visuals);
    }

    public MenuRenderResult render(Player player, MenuSession session) {
        MenuTemplate template = template(session);
        String cacheKey = PageModelBuilder.cacheKey(session);
        PageModel model = loadPageModel(player, session, template, cacheKey);
        return renderPrepared(player, session, model);
    }

    /** Returns the current visual template for the session's view. */
    public MenuTemplate template(MenuSession session) {
        MenuTemplate template = templates.get(session.currentView());
        return template == null ? MenuTemplate.empty() : template;
    }

    /** Returns true if this view is globally cacheable and can be prepared async. */
    public boolean canPrepareAsync(MenuSession session) {
        return template(session).cacheable();
    }

    /** Renders a previously prepared plain PageModel on the main server thread. */
    public MenuRenderResult renderPrepared(Player player, MenuSession session, PageModel model) {
        String cacheKey = PageModelBuilder.cacheKey(session);
        Inventory inventory = Bukkit.createInventory(
                new MarketMenuHolder(session.currentView(), cacheKey), DEFAULT_SIZE, title(session));
        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        renderModel(inventory, actions, session, model);
        return new MenuRenderResult(inventory, actions);
    }

    /** Lightweight shell shown while a heavy cacheable page model is prepared async. */
    public MenuRenderResult renderLoading(MenuSession session) {
        String cacheKey = PageModelBuilder.cacheKey(session) + ":loading";
        Inventory inventory = Bukkit.createInventory(
                new MarketMenuHolder(session.currentView(), cacheKey), DEFAULT_SIZE, title(session));
        Map<Integer, MenuAction> dummyActions = new LinkedHashMap<>();
        BorderTemplate.WITHOUT_BACK.apply(inventory, dummyActions, ctx);
        inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>Loading market page...</yellow>",
                List.of("<gray>The market is preparing this page from cached data.</gray>")));
        return new MenuRenderResult(inventory, Map.of());
    }

    private PageModel loadPageModel(Player player, MenuSession session, MenuTemplate template, String cacheKey) {
        if (template.cacheable()) {
            MenuDataVersionSnapshot current = dataVersion.snapshot();
            PageModel cached = pageCache.get(cacheKey, current, template.watchedDomains());
            if (cached != null) {
                return cached;
            }

            PageModel built = pageModelBuilder.build(player, session, template.pageSize());
            pageCache.put(cacheKey, built, current);
            return built;
        }
        return pageModelBuilder.build(player, session, template.pageSize());
    }

    private void renderModel(Inventory inventory,
                             Map<Integer, MenuAction> actions,
                             MenuSession session,
                             PageModel model) {
        switch (model) {
            case PageModel.Main main -> renderMain(inventory, actions, main);
            case PageModel.CategoryBrowser category -> renderCategoryBrowser(inventory, actions, category);
            case PageModel.SearchResults search -> renderSearchResults(inventory, actions, search);
            case PageModel.ListingBrowser listings -> renderListingBrowser(inventory, actions, session, listings);
            case PageModel.ItemDetail detail -> renderItemDetail(inventory, actions, detail);
            case PageModel.MyListings myListings -> renderMyListings(inventory, actions, myListings);
            case PageModel.ClaimsPage claims -> renderClaims(inventory, actions, claims);
            case PageModel.ClaimDetail claim -> renderClaimDetail(inventory, actions, claim);
            case PageModel.SaleHistory sales -> renderSaleHistory(inventory, actions, sales);
            case PageModel.PriceHistory prices -> renderPriceHistory(inventory, actions, prices);
        }
    }

    private void renderMain(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.Main model) {
        BorderTemplate.WITHOUT_BACK.apply(inventory, actions, ctx);
        new PlayerSummaryHeaderTemplate(model.playerBalanceHundredths(),
                "main.claimEarnings", MenuSlots.MAIN_CLAIM_EARNINGS).apply(inventory, actions, ctx);

        put(inventory, actions, visuals.slot("main.myListings", MenuSlots.MAIN_MY_LISTINGS),
                ctx.itemFactory().infoItem("<gold>My Listings</gold>", List.of("<gray>View your active listings.</gray>")),
                MenuAction.openView(MenuView.MY_LISTINGS));
        put(inventory, actions, visuals.slot("main.claims", MenuSlots.MAIN_CLAIMS),
                ctx.itemFactory().infoItem("<gold>Claims</gold>", List.of("<gray>View pending item claims.</gray>")),
                MenuAction.openView(MenuView.CLAIMS));
        put(inventory, actions, visuals.slot("main.listHeldItem", MenuSlots.MAIN_LIST_HELD_ITEM),
                ctx.itemFactory().listHeldItemButton(), MenuAction.simple(MenuActionType.START_LISTING_PROMPT));
        put(inventory, actions, visuals.slot("main.search", MenuSlots.MAIN_SEARCH),
                ctx.itemFactory().searchButton(), MenuAction.simple(MenuActionType.START_SEARCH_PROMPT));
        put(inventory, actions, visuals.slot("main.allListings", MenuSlots.MAIN_ALL_LISTINGS),
                ctx.itemFactory().infoItem("<gold>All Listings</gold>", List.of("<gray>Browse every active listing.</gray>")),
                MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, null));

        int[] slots = visuals.slotList("main.categories", MenuSlots.MAIN_CATEGORY_SLOTS);
        List<CategorySummary> categories = model.categories();
        for (int i = 0; i < categories.size() && i < slots.length; i++) {
            CategorySummary category = categories.get(i);
            put(inventory, actions, slots[i], ctx.itemFactory().categoryItem(category),
                    MenuAction.token(MenuActionType.OPEN_CATEGORY, category.categoryId()));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderCategoryBrowser(Inventory inventory,
                                       Map<Integer, MenuAction> actions,
                                       PageModel.CategoryBrowser model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);

        if (model.enchantTargets() != null) {
            List<EnchantBrowseSummary> targets = model.enchantTargets();
            if (targets.isEmpty()) {
                inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No enchant groups</yellow>",
                        List.of("<gray>No active enchanted books were found.</gray>")));
                new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
                return;
            }
            for (int i = 0; i < targets.size() && i < slots.length; i++) {
                EnchantBrowseSummary target = targets.get(i);
                put(inventory, actions, slots[i], ctx.itemFactory().enchantTargetItem(target),
                        MenuAction.token(MenuActionType.OPEN_ENCHANT_TARGET, target.group().commandToken()));
            }
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }

        List<SubcategorySummary> groups = model.groups() == null ? List.of() : model.groups();
        if (groups.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No groups</yellow>",
                    List.of("<gray>No active listings were found in this category.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < groups.size() && i < slots.length; i++) {
            SubcategorySummary group = groups.get(i);
            put(inventory, actions, slots[i], ctx.itemFactory().marketGroupItem(group),
                    MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, group.marketKey()));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderSearchResults(Inventory inventory,
                                     Map<Integer, MenuAction> actions,
                                     PageModel.SearchResults model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        List<SubcategorySummary> groups = model.groups();
        if (groups.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No results</yellow>",
                    List.of("<gray>Use Search or /market search &lt;query&gt;.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < groups.size() && i < slots.length; i++) {
            SubcategorySummary group = groups.get(i);
            put(inventory, actions, slots[i], ctx.itemFactory().marketGroupItem(group),
                    MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, group.marketKey()));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderListingBrowser(Inventory inventory,
                                      Map<Integer, MenuAction> actions,
                                      MenuSession session,
                                      PageModel.ListingBrowser model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        put(inventory, actions, visuals.slot("listing.sort", MenuSlots.TOP_RIGHT),
                ctx.itemFactory().sortButton(model.sortMode()), MenuAction.simple(MenuActionType.SORT_CYCLE));

        int[] slots = visuals.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS);
        List<Listing> listings = model.listings();
        if (listings.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No listings</yellow>",
                    List.of("<gray>No active listings were found here.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < listings.size() && i < slots.length; i++) {
            Listing listing = listings.get(i);
            put(inventory, actions, slots[i], ctx.itemFactory().listingItem(listing),
                    MenuAction.uuid(MenuActionType.OPEN_LISTING, listing.listingId()));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderItemDetail(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.ItemDetail model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        Listing listing = model.listing();
        if (listing == null) {
            inventory.setItem(22, ctx.itemFactory().listingUnavailableItem());
            return;
        }

        inventory.setItem(13, ctx.itemFactory().listingItem(listing));
        new ConfirmCancelTemplate(listing, model.selectedQuantity(), model.canBuy(), model.canCancel())
                .apply(inventory, actions, ctx);
        AdminControlsTemplate.NONE.apply(inventory, actions, ctx);

        put(inventory, actions, visuals.slot("detail.saleHistory", MenuSlots.ITEM_DETAIL_SALE_HISTORY),
                ctx.itemFactory().saleHistoryButton(), MenuAction.simple(MenuActionType.OPEN_SALE_HISTORY));
        put(inventory, actions, visuals.slot("detail.priceHistory", MenuSlots.ITEM_DETAIL_PRICE_HISTORY),
                ctx.itemFactory().priceHistoryButton(), MenuAction.simple(MenuActionType.OPEN_PRICE_HISTORY));
    }

    private void renderMyListings(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.MyListings model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        put(inventory, actions, visuals.slot("main.listHeldItem", MenuSlots.MAIN_LIST_HELD_ITEM),
                ctx.itemFactory().listHeldItemButton(), MenuAction.simple(MenuActionType.START_LISTING_PROMPT));
        inventory.setItem(visuals.slot("listing.capacity", MenuSlots.TOP_RIGHT),
                ctx.itemFactory().listingCapacityInfo(model.listedCount(), model.maxListingSlots()));

        int[] slots = visuals.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS);
        List<Listing> listings = model.listings();
        int visibleListingSlots = Math.max(0, Math.min(slots.length, model.visibleListingSlots()));
        for (int i = 0; i < visibleListingSlots; i++) {
            if (i < listings.size()) {
                Listing listing = listings.get(i);
                put(inventory, actions, slots[i], ctx.itemFactory().listingItem(listing),
                        MenuAction.uuid(MenuActionType.OPEN_LISTING, listing.listingId()));
            } else {
                inventory.clear(slots[i]);
            }
        }
        for (int i = visibleListingSlots; i < slots.length; i++) {
            inventory.setItem(slots[i], ctx.staticCache().fillerBlack());
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderClaims(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.ClaimsPage model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        new PlayerSummaryHeaderTemplate(model.playerBalanceHundredths(),
                "claims.earnings", MenuSlots.CLAIMS_EARNINGS).apply(inventory, actions, ctx);

        int[] slots = visuals.slotList("claims.items", MenuSlots.CLAIM_SLOTS);
        List<ItemClaimRecord> claims = model.claims();
        if (claims.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No item claims</yellow>",
                    List.of("<gray>You do not have pending item claims.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < claims.size() && i < slots.length; i++) {
            ItemClaimRecord claim = claims.get(i);
            put(inventory, actions, slots[i], ctx.itemFactory().claimItem(claim),
                    MenuAction.uuid(MenuActionType.OPEN_CLAIM, claim.claimId()));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderClaimDetail(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.ClaimDetail model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        ItemClaimRecord claim = model.claim();
        if (claim == null) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<red>Claim unavailable</red>",
                    List.of("<gray>This claim was already redeemed or no longer belongs to you.</gray>")));
            return;
        }

        inventory.setItem(13, ctx.itemFactory().claimItem(claim));
        put(inventory, actions, visuals.slot("claimDetail.relist", MenuSlots.CLAIM_DETAIL_RELIST),
                ctx.itemFactory().relistClaimButton(claim), MenuAction.simple(MenuActionType.START_RELIST_PROMPT));
        put(inventory, actions, visuals.slot("claimDetail.claimOne", MenuSlots.CLAIM_DETAIL_ONE_CHUNK),
                ctx.itemFactory().claimOneChunkButton(claim), MenuAction.simple(MenuActionType.CLAIM_ONE_CHUNK));
        put(inventory, actions, visuals.slot("claimDetail.claimAll", MenuSlots.CLAIM_DETAIL_AS_MUCH_AS_FITS),
                ctx.itemFactory().claimAsMuchAsFitsButton(claim), MenuAction.simple(MenuActionType.CLAIM_AS_MUCH_AS_FITS));
    }

    private void renderSaleHistory(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.SaleHistory model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);
        int[] slots = visuals.slotList("history.entries", MenuSlots.HISTORY_SLOTS);
        List<SaleRecord> sales = model.sales();
        if (sales.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No sales</yellow>",
                    List.of("<gray>No sale history was found.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < sales.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], ctx.itemFactory().saleHistoryItem(sales.get(i)));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void renderPriceHistory(Inventory inventory, Map<Integer, MenuAction> actions, PageModel.PriceHistory model) {
        BorderTemplate.WITH_BACK.apply(inventory, actions, ctx);

        if (model.selectedMonth() == null || model.totalMonths() <= 0) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No price history</yellow>",
                    List.of("<gray>No recommendation-history months were found for this item.</gray>")));
            return;
        }

        inventory.setItem(visuals.slot("history.month", MenuSlots.PRICE_HISTORY_MONTH_CONTEXT),
                ctx.itemFactory().monthContextItem(model.selectedMonth(), model.monthIndex(), model.totalMonths()));
        addPriceHistoryMonthButtons(inventory, actions, model.monthIndex(), model.totalMonths());

        int[] slots = visuals.slotList("history.entries", MenuSlots.HISTORY_SLOTS);
        List<RecommendationHistoryPoint> points = model.points();
        if (points.isEmpty()) {
            inventory.setItem(22, ctx.itemFactory().infoItem("<yellow>No price points</yellow>",
                    List.of("<gray>This month no longer has recommendation-history entries.</gray>")));
            new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
            return;
        }
        for (int i = 0; i < points.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], ctx.itemFactory().priceHistoryItem(points.get(i)));
        }
        new PaginationFooterTemplate(model.hasPrevious(), model.hasNext()).apply(inventory, actions, ctx);
    }

    private void addPriceHistoryMonthButtons(Inventory inventory, Map<Integer, MenuAction> actions,
                                              int monthIndex, int monthCount) {
        if (monthIndex + 1 < monthCount) {
            put(inventory, actions, visuals.slot("history.previousMonth", MenuSlots.PRICE_HISTORY_PREVIOUS_MONTH),
                    ctx.staticCache().previousMonth(), MenuAction.simple(MenuActionType.PREVIOUS_PRICE_HISTORY_MONTH));
        }
        if (monthIndex > 0) {
            put(inventory, actions, visuals.slot("history.nextMonth", MenuSlots.PRICE_HISTORY_NEXT_MONTH),
                    ctx.staticCache().nextMonth(), MenuAction.simple(MenuActionType.NEXT_PRICE_HISTORY_MONTH));
        }
    }

    private void put(Inventory inventory, Map<Integer, MenuAction> actions, int slot, ItemStack item, MenuAction action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null && action.type() != MenuActionType.NONE) {
            actions.put(slot, action);
        }
    }

    private String title(MenuSession session) {
        String fallback = dynamicTitle(session);
        String configured = visuals.title(session.currentView(), fallback);
        return resolveTitleTemplate(configured, fallback, session);
    }

    private String dynamicTitle(MenuSession session) {
        return switch (session.currentView()) {
            case MAIN -> "Divine Market";
            case CATEGORY_BROWSER -> categoryBrowserTitle(session);
            case SEARCH_RESULTS -> session.searchQuery() == null || session.searchQuery().isBlank()
                    ? "Market Search"
                    : "Search: " + session.searchQuery().trim();
            case LISTING_BROWSER -> listingBrowserTitle(session);
            case ITEM_DETAIL -> itemDetailTitle(session);
            case MY_LISTINGS -> "My Listings";
            case CLAIMS -> "Market Claims";
            case CLAIM_DETAIL -> "Claim Detail";
            case SALE_HISTORY -> marketDisplayName(session) + " Sale History";
            case PRICE_HISTORY -> marketDisplayName(session) + " Price History";
        };
    }

    private String categoryBrowserTitle(MenuSession session) {
        if ("enchanted_books".equalsIgnoreCase(session.selectedCategoryId())
                && session.selectedEnchantGroup() != null
                && !session.selectedEnchantGroup().isBlank()) {
            return divinejason.divinemarketplace.auction.model.EnchantBrowseGroup
                    .fromCommandToken(session.selectedEnchantGroup()).displayName() + " Enchants";
        }
        return dataFacade.getCategoryDisplayName(session.selectedCategoryId());
    }

    private String listingBrowserTitle(MenuSession session) {
        if (session.selectedMarketKey() == null || session.selectedMarketKey().isBlank()) {
            return "All Listings";
        }
        return marketDisplayName(session) + " Listings";
    }

    private String itemDetailTitle(MenuSession session) {
        Listing listing = dataFacade.getListingById(session.selectedListingId());
        if (listing != null) {
            return listing.marketDisplayName();
        }
        return marketDisplayName(session);
    }

    private String marketDisplayName(MenuSession session) {
        return dataFacade.getMarketDisplayName(session.selectedMarketKey());
    }

    private String resolveTitleTemplate(String configured, String fallback, MenuSession session) {
        if (configured == null || configured.isBlank() || isLegacyGenericTitle(session.currentView(), configured)) {
            return fallback;
        }
        String title = configured
                .replace("{category}", categoryBrowserTitle(session))
                .replace("{market_group}", marketDisplayName(session))
                .replace("{query}", session.searchQuery() == null ? "" : session.searchQuery().trim());
        return title.isBlank() ? fallback : title;
    }

    private boolean isLegacyGenericTitle(MenuView view, String title) {
        String normalized = title.trim();
        return switch (view) {
            case CATEGORY_BROWSER -> normalized.equalsIgnoreCase("Market Category");
            case SEARCH_RESULTS -> normalized.equalsIgnoreCase("Market Search");
            case LISTING_BROWSER -> normalized.equalsIgnoreCase("Market Listings");
            case ITEM_DETAIL -> normalized.equalsIgnoreCase("Market Item");
            case SALE_HISTORY -> normalized.equalsIgnoreCase("Sale History");
            case PRICE_HISTORY -> normalized.equalsIgnoreCase("Price History");
            default -> false;
        };
    }
}
