package divinejason.divinemarketplace.menu;


/*
 * File role: Builds Bukkit inventories from MenuSession state and returns the matching slot-action map for the listener.
 */
import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds inventories from MenuSession state + menu-ready data.
 *
 * Renderer rules:
 * - no buying/cancelling/claiming logic here
 * - every render returns a matching slot-action map
 * - listing and claim details always re-fetch from the live stores
 */
public final class MenuRenderer {
    private static final int DEFAULT_SIZE = 54;
    private static final String ADMIN_PERMISSION = "divinemarketplace.admin";
    private static final String ADMIN_CANCEL_PERMISSION = "divinemarketplace.admin.listing.cancel";

    private final MenuDataFacade dataFacade;
    private final MenuItemFactory itemFactory;
    private final MenuVisualConfig visuals;

    public MenuRenderer(MenuDataFacade dataFacade, MenuItemFactory itemFactory, MenuVisualConfig visuals) {
        this.dataFacade = dataFacade;
        this.itemFactory = itemFactory;
        this.visuals = visuals;
    }

    public MenuRenderResult render(Player player, MenuSession session) {
        Inventory inventory = Bukkit.createInventory(
                new MarketMenuHolder(session.currentView(), contextKey(session)),
                DEFAULT_SIZE,
                title(session)
        );
        Map<Integer, MenuAction> actions = new LinkedHashMap<>();
        fill(inventory);

        switch (session.currentView()) {
            case MAIN -> renderMain(inventory, actions, player, session);
            case CATEGORY_BROWSER -> renderCategoryBrowser(inventory, actions, session);
            case SEARCH_RESULTS -> renderSearchResults(inventory, actions, session);
            case LISTING_BROWSER -> renderListingBrowser(inventory, actions, session);
            case ITEM_DETAIL -> renderItemDetail(inventory, actions, player, session);
            case MY_LISTINGS -> renderMyListings(inventory, actions, player, session);
            case CLAIMS -> renderClaims(inventory, actions, player, session);
            case CLAIM_DETAIL -> renderClaimDetail(inventory, actions, player, session);
            case SALE_HISTORY -> renderSaleHistory(inventory, actions, session);
            case PRICE_HISTORY -> renderPriceHistory(inventory, actions, session);
        }
        return new MenuRenderResult(inventory, actions);
    }

    private void renderMain(Inventory inventory, Map<Integer, MenuAction> actions, Player player, MenuSession session) {
        put(inventory, actions, visuals.slot("main.myListings", MenuSlots.MAIN_MY_LISTINGS), itemFactory.infoItem("<gold>My Listings</gold>", List.of("<gray>View your active listings.</gray>")), MenuAction.openView(MenuView.MY_LISTINGS));
        put(inventory, actions, visuals.slot("main.claims", MenuSlots.MAIN_CLAIMS), itemFactory.infoItem("<gold>Claims</gold>", List.of("<gray>View pending item claims.</gray>")), MenuAction.openView(MenuView.CLAIMS));
        put(inventory, actions, visuals.slot("main.claimEarnings", MenuSlots.MAIN_CLAIM_EARNINGS), itemFactory.claimEarningsButton(dataFacade.getPlayerMoneyClaimBalance(player.getUniqueId())), MenuAction.simple(MenuActionType.CLAIM_EARNINGS));
        put(inventory, actions, visuals.slot("main.listHeldItem", MenuSlots.MAIN_LIST_HELD_ITEM), itemFactory.listHeldItemButton(), MenuAction.simple(MenuActionType.START_LISTING_PROMPT));
        put(inventory, actions, visuals.slot("main.search", MenuSlots.MAIN_SEARCH), itemFactory.searchButton(), MenuAction.simple(MenuActionType.START_SEARCH_PROMPT));
        put(inventory, actions, visuals.slot("main.allListings", MenuSlots.MAIN_ALL_LISTINGS), itemFactory.infoItem("<gold>All Listings</gold>", List.of("<gray>Browse every active listing.</gray>")), MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, null));

        int[] slots = visuals.slotList("main.categories", MenuSlots.MAIN_CATEGORY_SLOTS);
        MenuPage<CategorySummary> categories = dataFacade.getMainCategoriesPage(session.page(), slots.length);
        for (int i = 0; i < categories.items().size() && i < slots.length; i++) {
            CategorySummary category = categories.items().get(i);
            put(inventory, actions, slots[i], itemFactory.categoryItem(category), MenuAction.token(MenuActionType.OPEN_CATEGORY, category.categoryId()));
        }
        addPageButtons(inventory, actions, categories);
    }

    private void renderCategoryBrowser(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        putBack(inventory, actions);
        if ("enchanted_books".equalsIgnoreCase(session.selectedCategoryId())) {
            if (session.selectedEnchantGroup() == null || session.selectedEnchantGroup().isBlank()) {
                renderEnchantTargets(inventory, actions, session);
            } else {
                renderEnchantMarketGroups(inventory, actions, session);
            }
            return;
        }

        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        MenuPage<SubcategorySummary> groups = dataFacade.getMarketGroupsForCategoryPage(session.selectedCategoryId(), session.page(), slots.length);
        if (groups.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No groups</yellow>", List.of("<gray>No active listings were found in this category.</gray>")));
            addPageButtons(inventory, actions, groups);
            return;
        }
        for (int i = 0; i < groups.items().size() && i < slots.length; i++) {
            SubcategorySummary group = groups.items().get(i);
            put(inventory, actions, slots[i], itemFactory.marketGroupItem(group), MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, group.marketKey()));
        }
        addPageButtons(inventory, actions, groups);
    }

    private void renderEnchantTargets(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        MenuPage<EnchantBrowseSummary> groups = dataFacade.getEnchantedBookTargetGroupsPage(session.page(), slots.length);
        if (groups.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No enchant groups</yellow>", List.of("<gray>No active enchanted books were found.</gray>")));
            addPageButtons(inventory, actions, groups);
            return;
        }
        for (int i = 0; i < groups.items().size() && i < slots.length; i++) {
            EnchantBrowseSummary group = groups.items().get(i);
            put(inventory, actions, slots[i], itemFactory.enchantTargetItem(group), MenuAction.token(MenuActionType.OPEN_ENCHANT_TARGET, group.group().commandToken()));
        }
        addPageButtons(inventory, actions, groups);
    }

    private void renderEnchantMarketGroups(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        EnchantBrowseGroup browseGroup = EnchantBrowseGroup.fromCommandToken(session.selectedEnchantGroup());
        MenuPage<SubcategorySummary> books = dataFacade.getEnchantedBookMarketGroupsPage(browseGroup, session.page(), slots.length);
        if (books.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No books</yellow>", List.of("<gray>No active books were found for this target.</gray>")));
            addPageButtons(inventory, actions, books);
            return;
        }
        for (int i = 0; i < books.items().size() && i < slots.length; i++) {
            SubcategorySummary book = books.items().get(i);
            put(inventory, actions, slots[i], itemFactory.marketGroupItem(book), MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, book.marketKey()));
        }
        addPageButtons(inventory, actions, books);
    }

    private void renderSearchResults(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        putBack(inventory, actions);
        int[] slots = visuals.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        MenuPage<SubcategorySummary> groups = dataFacade.searchMarketGroupsPage(session.searchQuery(), session.page(), slots.length);
        if (groups.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No search text</yellow>", List.of("<gray>Use the Search button or /market search &lt;query&gt;.</gray>")));
            addPageButtons(inventory, actions, groups);
            return;
        }
        for (int i = 0; i < groups.items().size() && i < slots.length; i++) {
            SubcategorySummary group = groups.items().get(i);
            put(inventory, actions, slots[i], itemFactory.marketGroupItem(group), MenuAction.token(MenuActionType.OPEN_MARKET_GROUP, group.marketKey()));
        }
        addPageButtons(inventory, actions, groups);
    }

    private void renderListingBrowser(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        putBack(inventory, actions);
        put(inventory, actions, visuals.slot("listing.sort", MenuSlots.TOP_RIGHT), itemFactory.sortButton(session.sortMode()), MenuAction.simple(MenuActionType.SORT_CYCLE));
        int[] slots = visuals.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS);
        MenuPage<Listing> listings = dataFacade.getListingsPage(session.selectedMarketKey(), session.sortMode(), session.page(), slots.length);
        if (listings.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No listings</yellow>", List.of("<gray>No active listings were found here.</gray>")));
            addPageButtons(inventory, actions, listings);
            return;
        }
        for (int i = 0; i < listings.items().size() && i < slots.length; i++) {
            Listing listing = listings.items().get(i);
            put(inventory, actions, slots[i], itemFactory.listingItem(listing), MenuAction.uuid(MenuActionType.OPEN_LISTING, listing.listingId()));
        }
        addPageButtons(inventory, actions, listings);
    }

    private void renderItemDetail(Inventory inventory, Map<Integer, MenuAction> actions, Player player, MenuSession session) {
        putBack(inventory, actions);
        Listing listing = dataFacade.getListingById(session.selectedListingId());
        if (listing == null) {
            inventory.setItem(22, itemFactory.listingUnavailableItem());
            return;
        }

        boolean ownsListing = player.getUniqueId().equals(listing.sellerUuid());
        boolean mayAdminCancel = mayAdminCancel(player);
        boolean canBuy = !ownsListing;
        boolean canCancel = ownsListing || mayAdminCancel;

        int quantity = Math.max(1, Math.min(session.selectedQuantity(), listing.amount()));
        inventory.setItem(13, itemFactory.listingItem(listing));

        if (canBuy) {
            put(inventory, actions, visuals.slot("detail.decrease", MenuSlots.ITEM_DETAIL_DECREASE), itemFactory.decreaseArrow(), MenuAction.simple(MenuActionType.QUANTITY_DECREASE));
            inventory.setItem(visuals.slot("detail.quantity", MenuSlots.ITEM_DETAIL_QUANTITY), itemFactory.quantityPaper(quantity, listing.unitPrice(), listing.amount()));
            put(inventory, actions, visuals.slot("detail.increase", MenuSlots.ITEM_DETAIL_INCREASE), itemFactory.increaseArrow(), MenuAction.simple(MenuActionType.QUANTITY_INCREASE));
            put(inventory, actions, visuals.slot("detail.confirm", MenuSlots.ITEM_DETAIL_CONFIRM), itemFactory.confirmPurchaseButton(quantity, listing.unitPrice() * quantity), MenuAction.simple(MenuActionType.CONFIRM_PURCHASE));
        } else {
            inventory.setItem(visuals.slot("detail.quantity", MenuSlots.ITEM_DETAIL_QUANTITY), itemFactory.ownerListingInfo(listing));
        }

        if (canCancel) {
            put(inventory, actions, visuals.slot("detail.cancel", MenuSlots.ITEM_DETAIL_CANCEL), itemFactory.cancelListingButton(), MenuAction.simple(MenuActionType.CANCEL_LISTING));
        }

        put(inventory, actions, visuals.slot("detail.saleHistory", MenuSlots.ITEM_DETAIL_SALE_HISTORY), itemFactory.saleHistoryButton(), MenuAction.simple(MenuActionType.OPEN_SALE_HISTORY));
        put(inventory, actions, visuals.slot("detail.priceHistory", MenuSlots.ITEM_DETAIL_PRICE_HISTORY), itemFactory.priceHistoryButton(), MenuAction.simple(MenuActionType.OPEN_PRICE_HISTORY));
    }

    private void renderMyListings(Inventory inventory, Map<Integer, MenuAction> actions, Player player, MenuSession session) {
        putBack(inventory, actions);
        int[] slots = visuals.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS);
        MenuPage<Listing> listings = dataFacade.getPlayerListingsPage(player.getUniqueId(), session.page(), slots.length);
        if (listings.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No listings</yellow>", List.of("<gray>You have no active listings.</gray>")));
            addPageButtons(inventory, actions, listings);
            return;
        }
        for (int i = 0; i < listings.items().size() && i < slots.length; i++) {
            Listing listing = listings.items().get(i);
            put(inventory, actions, slots[i], itemFactory.listingItem(listing), MenuAction.uuid(MenuActionType.OPEN_LISTING, listing.listingId()));
        }
        addPageButtons(inventory, actions, listings);
    }

    private void renderClaims(Inventory inventory, Map<Integer, MenuAction> actions, Player player, MenuSession session) {
        putBack(inventory, actions);
        put(inventory, actions, visuals.slot("claims.earnings", MenuSlots.CLAIMS_EARNINGS), itemFactory.claimEarningsButton(dataFacade.getPlayerMoneyClaimBalance(player.getUniqueId())), MenuAction.simple(MenuActionType.CLAIM_EARNINGS));
        int[] slots = visuals.slotList("claims.items", MenuSlots.CLAIM_SLOTS);
        MenuPage<ItemClaimRecord> claims = dataFacade.getPlayerItemClaimsPage(player.getUniqueId(), session.page(), slots.length);
        if (claims.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No item claims</yellow>", List.of("<gray>You do not have pending item claims.</gray>")));
            addPageButtons(inventory, actions, claims);
            return;
        }
        for (int i = 0; i < claims.items().size() && i < slots.length; i++) {
            ItemClaimRecord claim = claims.items().get(i);
            put(inventory, actions, slots[i], itemFactory.claimItem(claim), MenuAction.uuid(MenuActionType.OPEN_CLAIM, claim.claimId()));
        }
        addPageButtons(inventory, actions, claims);
    }

    private void renderClaimDetail(Inventory inventory, Map<Integer, MenuAction> actions, Player player, MenuSession session) {
        putBack(inventory, actions);
        ItemClaimRecord claim = dataFacade.getPlayerItemClaimById(player.getUniqueId(), session.selectedClaimId());
        if (claim == null) {
            inventory.setItem(22, itemFactory.infoItem("<red>Claim unavailable</red>", List.of("<gray>This claim was already redeemed or no longer belongs to you.</gray>")));
            return;
        }

        inventory.setItem(13, itemFactory.claimItem(claim));
        put(inventory, actions, visuals.slot("claimDetail.relist", MenuSlots.CLAIM_DETAIL_RELIST), itemFactory.relistClaimButton(claim), MenuAction.simple(MenuActionType.START_RELIST_PROMPT));
        put(inventory, actions, visuals.slot("claimDetail.claimOne", MenuSlots.CLAIM_DETAIL_ONE_CHUNK), itemFactory.claimOneChunkButton(claim), MenuAction.simple(MenuActionType.CLAIM_ONE_CHUNK));
        put(inventory, actions, visuals.slot("claimDetail.claimAll", MenuSlots.CLAIM_DETAIL_AS_MUCH_AS_FITS), itemFactory.claimAsMuchAsFitsButton(claim), MenuAction.simple(MenuActionType.CLAIM_AS_MUCH_AS_FITS));
    }

    private void renderSaleHistory(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        putBack(inventory, actions);
        int[] slots = visuals.slotList("history.entries", MenuSlots.HISTORY_SLOTS);
        MenuPage<SaleRecord> sales = dataFacade.getSaleHistoryPage(session.selectedMarketKey(), session.page(), slots.length);
        if (sales.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No sales</yellow>", List.of("<gray>No sale history was found.</gray>")));
            addPageButtons(inventory, actions, sales);
            return;
        }
        for (int i = 0; i < sales.items().size() && i < slots.length; i++) {
            inventory.setItem(slots[i], itemFactory.saleHistoryItem(sales.items().get(i)));
        }
        addPageButtons(inventory, actions, sales);
    }

    private void renderPriceHistory(Inventory inventory, Map<Integer, MenuAction> actions, MenuSession session) {
        putBack(inventory, actions);

        List<YearMonth> months = dataFacade.getPriceHistoryMonths(session.selectedMarketKey());
        if (months.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No price history</yellow>", List.of("<gray>No recommendation-history months were found for this item.</gray>")));
            return;
        }

        YearMonth month = resolvePriceHistoryMonth(session, months);
        int monthIndex = months.indexOf(month);
        inventory.setItem(visuals.slot("history.month", MenuSlots.PRICE_HISTORY_MONTH_CONTEXT), itemFactory.monthContextItem(month, monthIndex, months.size()));
        addPriceHistoryMonthButtons(inventory, actions, monthIndex, months.size());

        int[] slots = visuals.slotList("history.entries", MenuSlots.HISTORY_SLOTS);
        MenuPage<RecommendationHistoryPoint> points = dataFacade.getPriceHistoryPage(session.selectedMarketKey(), month, session.page(), slots.length);
        if (points.isEmpty()) {
            inventory.setItem(22, itemFactory.infoItem("<yellow>No price points</yellow>", List.of("<gray>This month no longer has recommendation-history entries.</gray>")));
            addPageButtons(inventory, actions, points);
            return;
        }
        for (int i = 0; i < points.items().size() && i < slots.length; i++) {
            inventory.setItem(slots[i], itemFactory.priceHistoryItem(points.items().get(i)));
        }
        addPageButtons(inventory, actions, points);
    }

    private YearMonth resolvePriceHistoryMonth(MenuSession session, List<YearMonth> months) {
        YearMonth selected = session.selectedPriceHistoryMonth();
        if (selected != null && months.contains(selected)) {
            return selected;
        }
        return months.get(0);
    }

    private void addPriceHistoryMonthButtons(Inventory inventory, Map<Integer, MenuAction> actions, int monthIndex, int monthCount) {
        if (monthIndex + 1 < monthCount) {
            put(inventory, actions, visuals.slot("history.previousMonth", MenuSlots.PRICE_HISTORY_PREVIOUS_MONTH), itemFactory.previousMonthButton(), MenuAction.simple(MenuActionType.PREVIOUS_PRICE_HISTORY_MONTH));
        }
        if (monthIndex > 0) {
            put(inventory, actions, visuals.slot("history.nextMonth", MenuSlots.PRICE_HISTORY_NEXT_MONTH), itemFactory.nextMonthButton(), MenuAction.simple(MenuActionType.NEXT_PRICE_HISTORY_MONTH));
        }
    }

    private void addPageButtons(Inventory inventory, Map<Integer, MenuAction> actions, MenuPage<?> page) {
        if (page.hasPrevious()) {
            put(inventory, actions, visuals.slot("nav.previous", MenuSlots.PREVIOUS_PAGE), itemFactory.previousPageButton(), MenuAction.simple(MenuActionType.PREVIOUS_PAGE));
        }
        if (page.hasNext()) {
            put(inventory, actions, visuals.slot("nav.next", MenuSlots.NEXT_PAGE), itemFactory.nextPageButton(), MenuAction.simple(MenuActionType.NEXT_PAGE));
        }
    }

    private void putBack(Inventory inventory, Map<Integer, MenuAction> actions) {
        put(inventory, actions, visuals.slot("nav.back", MenuSlots.BACK), itemFactory.backButton(), MenuAction.simple(MenuActionType.BACK));
    }

    private boolean mayAdminCancel(Player player) {
        return player.hasPermission(ADMIN_PERMISSION) || player.hasPermission(ADMIN_CANCEL_PERMISSION);
    }

    private void put(Inventory inventory, Map<Integer, MenuAction> actions, int slot, org.bukkit.inventory.ItemStack item, MenuAction action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null && action.type() != MenuActionType.NONE) {
            actions.put(slot, action);
        }
    }

    private void fill(Inventory inventory) {
        var filler = itemFactory.fillerBlack();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private String title(MenuSession session) {
        return visuals.title(session.currentView(), switch (session.currentView()) {
            case MAIN -> "Market";
            case CATEGORY_BROWSER -> "Market Category";
            case SEARCH_RESULTS -> "Market Search";
            case LISTING_BROWSER -> "Market Listings";
            case ITEM_DETAIL -> "Market Item";
            case MY_LISTINGS -> "My Listings";
            case CLAIMS -> "Market Claims";
            case CLAIM_DETAIL -> "Claim Detail";
            case SALE_HISTORY -> "Sale History";
            case PRICE_HISTORY -> "Price History";
        });
    }

    private String contextKey(MenuSession session) {
        return session.currentView().name().toLowerCase(java.util.Locale.ROOT)
                + ":page=" + session.page()
                + ":category=" + nullToEmpty(session.selectedCategoryId())
                + ":enchant=" + nullToEmpty(session.selectedEnchantGroup())
                + ":market=" + nullToEmpty(session.selectedMarketKey())
                + ":listing=" + (session.selectedListingId() == null ? "" : session.selectedListingId())
                + ":claim=" + (session.selectedClaimId() == null ? "" : session.selectedClaimId())
                + ":priceMonth=" + (session.selectedPriceHistoryMonth() == null ? "" : session.selectedPriceHistoryMonth());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
