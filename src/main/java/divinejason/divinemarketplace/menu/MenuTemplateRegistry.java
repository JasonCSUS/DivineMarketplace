package divinejason.divinemarketplace.menu;

/*
 * File role: Builds one MenuTemplate per MenuView from the current MenuVisualConfig.
 */

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Constructed once per MenuVisualConfig lifetime.  When the visual config is
 * reloaded, a new registry is built inside the new MenuRenderer instance.
 */
public final class MenuTemplateRegistry {

    private final Map<MenuView, MenuTemplate> templates = new EnumMap<>(MenuView.class);

    public MenuTemplateRegistry(MenuVisualConfig visuals) {
        templates.put(MenuView.MAIN,             buildMain(visuals));
        templates.put(MenuView.CATEGORY_BROWSER, buildCategoryBrowser(visuals));
        templates.put(MenuView.SEARCH_RESULTS,   buildSearchResults(visuals));
        templates.put(MenuView.LISTING_BROWSER,  buildListingBrowser(visuals));
        templates.put(MenuView.ITEM_DETAIL,      buildItemDetail(visuals));
        templates.put(MenuView.MY_LISTINGS,      buildMyListings(visuals));
        templates.put(MenuView.CLAIMS,           buildClaims(visuals));
        templates.put(MenuView.CLAIM_DETAIL,     buildClaimDetail(visuals));
        templates.put(MenuView.SALE_HISTORY,     buildSaleHistory(visuals));
        templates.put(MenuView.PRICE_HISTORY,    buildPriceHistory(visuals));
    }

    public MenuTemplate get(MenuView view) {
        return templates.get(view);
    }

    // -------------------------------------------------------------------------
    // Per-view builders
    // -------------------------------------------------------------------------

    private static MenuTemplate buildMain(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("main.myListings",   MenuSlots.MAIN_MY_LISTINGS));
        mark(mask, v.slot("main.claims",        MenuSlots.MAIN_CLAIMS));
        mark(mask, v.slot("main.claimEarnings", MenuSlots.MAIN_CLAIM_EARNINGS));
        mark(mask, v.slot("main.listHeldItem",  MenuSlots.MAIN_LIST_HELD_ITEM));
        mark(mask, v.slot("main.search",        MenuSlots.MAIN_SEARCH));
        mark(mask, v.slot("main.allListings",   MenuSlots.MAIN_ALL_LISTINGS));
        mark(mask, v.slot("nav.previous",       MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",           MenuSlots.NEXT_PAGE));
        markAll(mask, v.slotList("main.categories", MenuSlots.MAIN_CATEGORY_SLOTS));
        // Player-specific chrome (earnings, listing count) — not cacheable across players.
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS, DataDomain.CLAIMS), false, v.slotList("main.categories", MenuSlots.MAIN_CATEGORY_SLOTS).length);
    }

    private static MenuTemplate buildCategoryBrowser(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        int[] contentSlots = v.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        mark(mask, v.slot("nav.back",     MenuSlots.BACK));
        mark(mask, v.slot("nav.previous", MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",     MenuSlots.NEXT_PAGE));
        markAll(mask, contentSlots);
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS, DataDomain.CATEGORIES), true, contentSlots.length);
    }

    private static MenuTemplate buildSearchResults(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        int[] contentSlots = v.slotList("category.groups", MenuSlots.CATEGORY_BROWSER_SLOTS);
        mark(mask, v.slot("nav.back",     MenuSlots.BACK));
        mark(mask, v.slot("nav.previous", MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",     MenuSlots.NEXT_PAGE));
        markAll(mask, contentSlots);
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS, DataDomain.CATEGORIES), true, contentSlots.length);
    }

    private static MenuTemplate buildListingBrowser(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        int[] contentSlots = v.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS);
        mark(mask, v.slot("nav.back",      MenuSlots.BACK));
        mark(mask, v.slot("listing.sort",  MenuSlots.TOP_RIGHT));
        mark(mask, v.slot("nav.previous",  MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",      MenuSlots.NEXT_PAGE));
        markAll(mask, contentSlots);
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS, DataDomain.CATEGORIES), true, contentSlots.length);
    }

    private static MenuTemplate buildItemDetail(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",            MenuSlots.BACK));
        mark(mask, 13); // listing item preview
        mark(mask, v.slot("detail.decrease",     MenuSlots.ITEM_DETAIL_DECREASE));
        mark(mask, v.slot("detail.quantity",     MenuSlots.ITEM_DETAIL_QUANTITY));
        mark(mask, v.slot("detail.increase",     MenuSlots.ITEM_DETAIL_INCREASE));
        mark(mask, v.slot("detail.confirm",      MenuSlots.ITEM_DETAIL_CONFIRM));
        mark(mask, v.slot("detail.cancel",       MenuSlots.ITEM_DETAIL_CANCEL));
        mark(mask, v.slot("detail.saleHistory",  MenuSlots.ITEM_DETAIL_SALE_HISTORY));
        mark(mask, v.slot("detail.priceHistory", MenuSlots.ITEM_DETAIL_PRICE_HISTORY));
        // Player-specific (can-buy vs can-cancel depends on ownership) — not cacheable.
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS), false, 0);
    }

    private static MenuTemplate buildMyListings(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",          MenuSlots.BACK));
        mark(mask, v.slot("main.listHeldItem", MenuSlots.MAIN_LIST_HELD_ITEM));
        mark(mask, v.slot("listing.capacity",  MenuSlots.TOP_RIGHT));
        mark(mask, v.slot("nav.previous",      MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",          MenuSlots.NEXT_PAGE));
        markAll(mask, v.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS));
        // Player-specific — not cacheable.
        return new MenuTemplate(mask, Set.of(DataDomain.LISTINGS), false, v.slotList("listing.listings", MenuSlots.LISTING_BROWSER_SLOTS).length);
    }

    private static MenuTemplate buildClaims(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",       MenuSlots.BACK));
        mark(mask, v.slot("claims.earnings", MenuSlots.CLAIMS_EARNINGS));
        mark(mask, v.slot("nav.previous",   MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",       MenuSlots.NEXT_PAGE));
        markAll(mask, v.slotList("claims.items", MenuSlots.CLAIM_SLOTS));
        // Player-specific — not cacheable.
        return new MenuTemplate(mask, Set.of(DataDomain.CLAIMS), false, v.slotList("claims.items", MenuSlots.CLAIM_SLOTS).length);
    }

    private static MenuTemplate buildClaimDetail(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",              MenuSlots.BACK));
        mark(mask, 13); // claim item preview
        mark(mask, v.slot("claimDetail.relist",    MenuSlots.CLAIM_DETAIL_RELIST));
        mark(mask, v.slot("claimDetail.claimOne",  MenuSlots.CLAIM_DETAIL_ONE_CHUNK));
        mark(mask, v.slot("claimDetail.claimAll",  MenuSlots.CLAIM_DETAIL_AS_MUCH_AS_FITS));
        // Player-specific — not cacheable.
        return new MenuTemplate(mask, Set.of(DataDomain.CLAIMS), false, 0);
    }

    private static MenuTemplate buildSaleHistory(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",     MenuSlots.BACK));
        mark(mask, v.slot("nav.previous", MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",     MenuSlots.NEXT_PAGE));
        markAll(mask, v.slotList("history.entries", MenuSlots.HISTORY_SLOTS));
        return new MenuTemplate(mask, Set.of(DataDomain.SALES_HISTORY), true, v.slotList("history.entries", MenuSlots.HISTORY_SLOTS).length);
    }

    private static MenuTemplate buildPriceHistory(MenuVisualConfig v) {
        boolean[] mask = new boolean[54];
        mark(mask, v.slot("nav.back",              MenuSlots.BACK));
        mark(mask, v.slot("history.month",         MenuSlots.PRICE_HISTORY_MONTH_CONTEXT));
        mark(mask, v.slot("history.previousMonth", MenuSlots.PRICE_HISTORY_PREVIOUS_MONTH));
        mark(mask, v.slot("history.nextMonth",     MenuSlots.PRICE_HISTORY_NEXT_MONTH));
        mark(mask, v.slot("nav.previous",          MenuSlots.PREVIOUS_PAGE));
        mark(mask, v.slot("nav.next",              MenuSlots.NEXT_PAGE));
        markAll(mask, v.slotList("history.entries", MenuSlots.HISTORY_SLOTS));
        return new MenuTemplate(mask, Set.of(DataDomain.PRICES), true, v.slotList("history.entries", MenuSlots.HISTORY_SLOTS).length);
    }

    // -------------------------------------------------------------------------
    // Mask helpers
    // -------------------------------------------------------------------------

    private static void mark(boolean[] mask, int slot) {
        if (slot >= 0 && slot < mask.length) mask[slot] = true;
    }

    private static void markAll(boolean[] mask, int[] slots) {
        for (int s : slots) mark(mask, s);
    }
}
