package divinejason.divinemarketplace.menu;

import divinejason.divinemarketplace.auction.model.SortMode;

import java.util.UUID;

/**
 * Per-player UI state only.
 *
 * MenuSession must never be treated as source-of-truth business data.
 * Listings, claims, balances, and history must always be re-fetched from live
 * stores/services before mutating anything.
 */
public record MenuSession(
        UUID playerUuid,
        MenuView currentView,
        int page,
        SortMode sortMode,
        String selectedCategoryId,
        String selectedMarketKey,
        String searchQuery,
        UUID selectedListingId,
        int selectedQuantity,
        boolean actionLocked
) {
    public static MenuSession create(UUID playerUuid) {
        return new MenuSession(
                playerUuid,
                MenuView.MAIN,
                0,
                SortMode.NEWEST_FIRST,
                null,
                null,
                null,
                null,
                1,
                false
        );
    }

    public MenuSession withView(MenuView view) {
        return new MenuSession(playerUuid, view, page, sortMode, selectedCategoryId, selectedMarketKey, searchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withPage(int newPage) {
        return new MenuSession(playerUuid, currentView, newPage, sortMode, selectedCategoryId, selectedMarketKey, searchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSortMode(SortMode newSortMode) {
        return new MenuSession(playerUuid, currentView, page, newSortMode, selectedCategoryId, selectedMarketKey, searchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSelectedCategoryId(String newCategoryId) {
        return new MenuSession(playerUuid, currentView, page, sortMode, newCategoryId, selectedMarketKey, searchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSelectedMarketKey(String newMarketKey) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, newMarketKey, searchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSearchQuery(String newSearchQuery) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedMarketKey, newSearchQuery, selectedListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSelectedListingId(UUID newListingId) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedMarketKey, searchQuery, newListingId, selectedQuantity, actionLocked);
    }

    public MenuSession withSelectedQuantity(int newQuantity) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedMarketKey, searchQuery, selectedListingId, newQuantity, actionLocked);
    }

    public MenuSession withActionLocked(boolean newActionLocked) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedMarketKey, searchQuery, selectedListingId, selectedQuantity, newActionLocked);
    }
}
