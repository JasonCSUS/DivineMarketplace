package divinejason.divinemarketplace.menu;

import divinejason.divinemarketplace.auction.model.SortMode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player UI state only.
 *
 * MenuSession is sharded by player UUID and stores navigation/input context for
 * the chest GUI. It must never be treated as source-of-truth business data:
 * listings, claims, balances, and histories are always re-fetched from live
 * services before a click mutates anything.
 */
public record MenuSession(
        UUID playerUuid,
        MenuView currentView,
        int page,
        SortMode sortMode,
        String selectedCategoryId,
        String selectedEnchantGroup,
        String selectedMarketKey,
        String searchQuery,
        UUID selectedListingId,
        UUID selectedClaimId,
        YearMonth selectedPriceHistoryMonth,
        int selectedQuantity,
        boolean actionLocked,
        List<MenuSnapshot> backStack
) {
    public MenuSession {
        page = Math.max(0, page);
        selectedQuantity = Math.max(1, selectedQuantity);
        backStack = List.copyOf(backStack == null ? List.of() : backStack);
    }

    public static MenuSession create(UUID playerUuid) {
        return new MenuSession(playerUuid, MenuView.MAIN, 0, SortMode.NEWEST_FIRST, null, null, null, null, null, null, null, 1, false, List.of());
    }

    public MenuSession withView(MenuView view) {
        return new MenuSession(playerUuid, view, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withPage(int newPage) {
        return new MenuSession(playerUuid, currentView, newPage, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSortMode(SortMode newSortMode) {
        return new MenuSession(playerUuid, currentView, page, newSortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedCategoryId(String newCategoryId) {
        return new MenuSession(playerUuid, currentView, page, sortMode, newCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedEnchantGroup(String newEnchantGroup) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, newEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedMarketKey(String newMarketKey) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, newMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSearchQuery(String newSearchQuery) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, newSearchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedListingId(UUID newListingId) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, newListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedClaimId(UUID newClaimId) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, newClaimId, selectedPriceHistoryMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedPriceHistoryMonth(YearMonth newMonth) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, newMonth, selectedQuantity, actionLocked, backStack);
    }

    public MenuSession withSelectedQuantity(int newQuantity) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, newQuantity, actionLocked, backStack);
    }

    public MenuSession withActionLocked(boolean newActionLocked) {
        return new MenuSession(playerUuid, currentView, page, sortMode, selectedCategoryId, selectedEnchantGroup, selectedMarketKey, searchQuery, selectedListingId, selectedClaimId, selectedPriceHistoryMonth, selectedQuantity, newActionLocked, backStack);
    }

    public MenuSession pushAndOpen(MenuSession destination) {
        List<MenuSnapshot> newStack = new ArrayList<>(backStack);
        newStack.add(MenuSnapshot.from(this));
        return new MenuSession(
                destination.playerUuid,
                destination.currentView,
                destination.page,
                destination.sortMode,
                destination.selectedCategoryId,
                destination.selectedEnchantGroup,
                destination.selectedMarketKey,
                destination.searchQuery,
                destination.selectedListingId,
                destination.selectedClaimId,
                destination.selectedPriceHistoryMonth,
                destination.selectedQuantity,
                false,
                newStack
        );
    }

    public MenuSession backOrMain() {
        if (backStack.isEmpty()) {
            return create(playerUuid);
        }

        List<MenuSnapshot> newStack = new ArrayList<>(backStack);
        MenuSnapshot snapshot = newStack.remove(newStack.size() - 1);
        return snapshot.restore(playerUuid, newStack);
    }

    public record MenuSnapshot(
            MenuView currentView,
            int page,
            SortMode sortMode,
            String selectedCategoryId,
            String selectedEnchantGroup,
            String selectedMarketKey,
            String searchQuery,
            UUID selectedListingId,
            UUID selectedClaimId,
            YearMonth selectedPriceHistoryMonth,
            int selectedQuantity
    ) {
        static MenuSnapshot from(MenuSession session) {
            return new MenuSnapshot(
                    session.currentView,
                    session.page,
                    session.sortMode,
                    session.selectedCategoryId,
                    session.selectedEnchantGroup,
                    session.selectedMarketKey,
                    session.searchQuery,
                    session.selectedListingId,
                    session.selectedClaimId,
                    session.selectedPriceHistoryMonth,
                    session.selectedQuantity
            );
        }

        MenuSession restore(UUID playerUuid, List<MenuSnapshot> backStack) {
            return new MenuSession(
                    playerUuid,
                    currentView,
                    page,
                    sortMode,
                    selectedCategoryId,
                    selectedEnchantGroup,
                    selectedMarketKey,
                    searchQuery,
                    selectedListingId,
                    selectedClaimId,
                    selectedPriceHistoryMonth,
                    selectedQuantity,
                    false,
                    backStack
            );
        }
    }
}
