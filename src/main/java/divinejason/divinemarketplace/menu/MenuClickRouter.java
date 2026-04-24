package divinejason.divinemarketplace.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Translates menu clicks into state changes or service calls.
 *
 * Router rules:
 * - listener stays thin
 * - router re-fetches live data before real actions
 * - services do business logic; router only routes
 */
public final class MenuClickRouter {
    private final MenuSessionManager sessionManager;
    private final MenuController menuController;

    public MenuClickRouter(MenuSessionManager sessionManager, MenuController menuController) {
        this.sessionManager = sessionManager;
        this.menuController = menuController;
    }

    public void handleClick(Player player, MarketMenuHolder holder, int rawSlot, ClickType clickType) {
        MenuSession session = sessionManager.getOrCreate(player.getUniqueId());

        if (session.actionLocked()) {
            return;
        }

        switch (session.currentView()) {
            case MAIN -> handleMainClick(player, session, rawSlot);
            case CATEGORY_BROWSER -> handleCategoryBrowserClick(player, session, rawSlot);
            case SEARCH_RESULTS -> handleSearchResultsClick(player, session, rawSlot);
            case LISTING_BROWSER -> handleListingBrowserClick(player, session, rawSlot);
            case ITEM_DETAIL -> handleItemDetailClick(player, session, rawSlot, clickType);
            case MY_LISTINGS -> handleMyListingsClick(player, session, rawSlot);
            case CLAIMS -> handleClaimsClick(player, session, rawSlot, clickType);
            case SALE_HISTORY -> handleSaleHistoryClick(player, session, rawSlot);
            case PRICE_HISTORY -> handlePriceHistoryClick(player, session, rawSlot);
        }
    }

    private void handleMainClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // claims / my listings / claim earnings / search instruction / all listings / category open
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleCategoryBrowserClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // open selected market-key group into LISTING_BROWSER
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleSearchResultsClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // same as category browser but backed by fuzzy search results
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleListingBrowserClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // back / page / sort cycle / sale history / price history / open item detail
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleItemDetailClick(Player player, MenuSession session, int rawSlot, ClickType clickType) {
        // PSEUDOCODE:
        // back
        // increase quantity (+1 or +64 on shift)
        // decrease quantity (-1 or -64 on shift)
        // confirm purchase
        // cancel listing (owner/admin only)
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleMyListingsClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // open selected listing in ITEM_DETAIL with cancel visibility as appropriate
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleClaimsClick(Player player, MenuSession session, int rawSlot, ClickType clickType) {
        // PSEUDOCODE:
        // claim earnings
        // claim one safe item chunk
        // shift-click claim as much as fits
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handleSaleHistoryClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // back / previous page / next page
        throw new UnsupportedOperationException("pseudocode scaffold");
    }

    private void handlePriceHistoryClick(Player player, MenuSession session, int rawSlot) {
        // PSEUDOCODE:
        // back / previous month / next month
        throw new UnsupportedOperationException("pseudocode scaffold");
    }
}
