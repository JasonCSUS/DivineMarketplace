package divinejason.divinemarketplace.menu;


/*
 * File role: Executes GUI click actions by updating menu state or calling live marketplace services on the Bukkit thread.
 */
import divinejason.divinemarketplace.auction.model.ClaimItemResult;
import divinejason.divinemarketplace.auction.model.ClaimMoneyResult;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.PurchaseResult;
import divinejason.divinemarketplace.auction.service.claim.ClaimService;
import divinejason.divinemarketplace.auction.service.listing.ListingService;
import divinejason.divinemarketplace.auction.service.purchase.PurchaseService;
import divinejason.divinemarketplace.concurrency.MarketActionGate;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Routes render-time menu actions. Mutations always re-fetch live data first. */
public final class MenuClickRouter {
    private final MenuSessionManager sessionManager;
    private final MenuController menuController;
    private final MenuDataFacade dataFacade;
    private final ListingService listingService;
    private final ClaimService claimService;
    private final PurchaseService purchaseService;
    private final MarketChatPromptService chatPromptService;
    private final MarketActionGate actionGate;
    private final BooleanSupplier readinessCheck;

    public MenuClickRouter(
            MenuSessionManager sessionManager,
            MenuController menuController,
            MenuDataFacade dataFacade,
            ListingService listingService,
            ClaimService claimService,
            PurchaseService purchaseService,
            MarketChatPromptService chatPromptService,
            MarketActionGate actionGate,
            BooleanSupplier readinessCheck
    ) {
        this.sessionManager = sessionManager;
        this.menuController = menuController;
        this.dataFacade = dataFacade;
        this.listingService = listingService;
        this.claimService = claimService;
        this.purchaseService = purchaseService;
        this.chatPromptService = chatPromptService;
        this.actionGate = actionGate;
        this.readinessCheck = readinessCheck;
    }

    public void handleClick(Player player, MarketMenuHolder holder, int rawSlot, ClickType clickType) {
        if (!readinessCheck.getAsBoolean()) {
            player.sendRichMessage("<yellow>The market is still loading. Try again in a moment.</yellow>");
            return;
        }
        MenuSession session = sessionManager.getOrCreate(player.getUniqueId());
        if (session.actionLocked() || actionGate.isPlayerLocked(player.getUniqueId())) {
            return;
        }
        MenuAction action = sessionManager.getAction(player.getUniqueId(), rawSlot).orElse(MenuAction.none());
        if (action.type() == MenuActionType.NONE) {
            return;
        }
        handleAction(player, session, action, clickType);
    }

    private void handleAction(Player player, MenuSession session, MenuAction action, ClickType clickType) {
        switch (action.type()) {
            case BACK -> menuController.open(player, session.backOrMain());
            case PREVIOUS_PAGE -> menuController.open(player, session.withPage(Math.max(0, session.page() - 1)));
            case NEXT_PAGE -> menuController.open(player, session.withPage(session.page() + 1));
            case OPEN_VIEW -> {
                if (action.targetView() != null) {
                    menuController.open(player, session.pushAndOpen(session.withView(action.targetView()).withPage(0)));
                }
            }
            case OPEN_CATEGORY -> menuController.open(player, session.pushAndOpen(session
                    .withSelectedCategoryId(action.token())
                    .withSelectedEnchantGroup(null)
                    .withSelectedMarketKey(null)
                    .withView(MenuView.CATEGORY_BROWSER)
                    .withPage(0)));
            case OPEN_ENCHANT_TARGET -> menuController.open(player, session.pushAndOpen(session
                    .withSelectedCategoryId("enchanted_books")
                    .withSelectedEnchantGroup(action.token())
                    .withSelectedMarketKey(null)
                    .withView(MenuView.CATEGORY_BROWSER)
                    .withPage(0)));
            case OPEN_MARKET_GROUP -> menuController.open(player, session.pushAndOpen(session
                    .withSelectedMarketKey(action.token())
                    .withSelectedListingId(null)
                    .withView(MenuView.LISTING_BROWSER)
                    .withPage(0)));
            case OPEN_LISTING -> openListingIfStillActive(player, session, action);
            case OPEN_CLAIM -> openClaimIfStillAvailable(player, session, action);
            case START_LISTING_PROMPT -> chatPromptService.promptListing(player, session);
            case START_RELIST_PROMPT -> startRelistPrompt(player, session);
            case START_SEARCH_PROMPT -> chatPromptService.promptSearch(player, session);
            case CLAIM_ONE_CHUNK -> claimOneChunk(player, session);
            case CLAIM_AS_MUCH_AS_FITS -> claimAsMuchAsFits(player, session);
            case CLAIM_EARNINGS -> claimEarnings(player, session);
            case SORT_CYCLE -> menuController.open(player, session.withSortMode(session.sortMode().next()).withPage(0));
            case QUANTITY_DECREASE -> adjustQuantity(player, session, clickType.isShiftClick() ? -64 : -1);
            case QUANTITY_INCREASE -> adjustQuantity(player, session, clickType.isShiftClick() ? 64 : 1);
            case CONFIRM_PURCHASE -> confirmPurchase(player, session);
            case CANCEL_LISTING -> cancelListing(player, session);
            case OPEN_SALE_HISTORY -> menuController.open(player, session.pushAndOpen(session.withView(MenuView.SALE_HISTORY).withPage(0)));
            case OPEN_PRICE_HISTORY -> menuController.open(player, session.pushAndOpen(session.withView(MenuView.PRICE_HISTORY).withSelectedPriceHistoryMonth(null).withPage(0)));
            case PREVIOUS_PRICE_HISTORY_MONTH -> movePriceHistoryMonth(player, session, 1);
            case NEXT_PRICE_HISTORY_MONTH -> movePriceHistoryMonth(player, session, -1);
            case NONE -> {
            }
        }
    }

    private void movePriceHistoryMonth(Player player, MenuSession session, int indexDelta) {
        List<YearMonth> months = dataFacade.getPriceHistoryMonths(session.selectedMarketKey());
        if (months.isEmpty()) {
            menuController.refresh(player);
            return;
        }

        YearMonth selected = session.selectedPriceHistoryMonth();
        int currentIndex = selected == null ? 0 : months.indexOf(selected);
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        int nextIndex = Math.max(0, Math.min(months.size() - 1, currentIndex + indexDelta));
        menuController.open(player, session.withSelectedPriceHistoryMonth(months.get(nextIndex)).withPage(0));
    }

    private void openListingIfStillActive(Player player, MenuSession session, MenuAction action) {
        Listing listing = action.uuid() == null ? null : dataFacade.getListingById(action.uuid());
        if (listing == null) {
            player.sendRichMessage("<yellow>That listing is no longer available.</yellow> <gray>The page was refreshed.</gray>");
            menuController.refresh(player);
            return;
        }
        menuController.open(player, session.pushAndOpen(session
                .withSelectedMarketKey(listing.marketKey())
                .withSelectedListingId(action.uuid())
                .withSelectedQuantity(1)
                .withView(MenuView.ITEM_DETAIL)
                .withPage(0)));
    }

    private void openClaimIfStillAvailable(Player player, MenuSession session, MenuAction action) {
        if (action.uuid() == null || dataFacade.getPlayerItemClaimById(player.getUniqueId(), action.uuid()) == null) {
            player.sendRichMessage("<yellow>That claim is no longer available.</yellow> <gray>The page was refreshed.</gray>");
            menuController.refresh(player);
            return;
        }
        menuController.open(player, session.pushAndOpen(session
                .withSelectedClaimId(action.uuid())
                .withView(MenuView.CLAIM_DETAIL)
                .withPage(0)));
    }

    private void startRelistPrompt(Player player, MenuSession session) {
        if (session.selectedClaimId() == null) {
            player.sendRichMessage("<yellow>No claim is selected.</yellow>");
            menuController.refresh(player);
            return;
        }
        chatPromptService.promptRelistClaim(player, session.selectedClaimId(), session.withActionLocked(false));
    }

    private void adjustQuantity(Player player, MenuSession session, int delta) {
        Listing listing = dataFacade.getListingById(session.selectedListingId());
        if (listing == null) {
            player.sendRichMessage("<yellow>That listing is no longer available.</yellow>");
            menuController.refresh(player);
            return;
        }
        int nextQuantity = Math.max(1, Math.min(listing.amount(), session.selectedQuantity() + delta));
        menuController.open(player, session.withSelectedQuantity(nextQuantity));
    }

    private void confirmPurchase(Player player, MenuSession session) {
        runLocked(player, session, () -> {
            Listing listing = dataFacade.getListingById(session.selectedListingId());
            if (listing == null) {
                player.sendRichMessage("<yellow>That listing is no longer available.</yellow>");
                unlockAndRefresh(player);
                return;
            }

            int quantity = Math.max(1, Math.min(session.selectedQuantity(), listing.amount()));
            PurchaseResult result = purchaseService.purchase(player, listing.listingId(), quantity);
            if (!result.success()) {
                player.sendRichMessage("<red>Purchase failed:</red> <gray>" + escapeMini(result.failureReason() == null ? result.debugMessage() : result.failureReason().name()) + "</gray>");
                unlockAndRefresh(player);
                return;
            }

            menuController.invalidation().markListingPurchaseCompleted();
            player.sendRichMessage("<green>Purchased</green> <white>" + escapeMini(result.marketDisplayName()) + "</white> <gray>x" + result.quantityPurchased() + ".</gray> <yellow>Items were moved to your claims.</yellow>");
            menuController.open(player, session.pushAndOpen(session
                    .withView(MenuView.CLAIMS)
                    .withSelectedListingId(null)
                    .withSelectedClaimId(null)
                    .withPage(0)
                    .withActionLocked(false)));
        }, resourceKey("listing", session.selectedListingId()));
    }

    private void cancelListing(Player player, MenuSession session) {
        runLocked(player, session, () -> {
            UUID listingId = session.selectedListingId();
            if (listingId == null || dataFacade.getListingById(listingId) == null) {
                player.sendRichMessage("<yellow>That listing is no longer available.</yellow>");
                unlockAndRefresh(player);
                return;
            }

            listingService.cancelListing(player, listingId);
            menuController.invalidation().markListingAndClaimsChanged();
            player.sendRichMessage("<green>Listing cancelled.</green> <gray>The item was moved to the seller's claims.</gray>");
            menuController.open(player, session.backOrMain());
        }, resourceKey("listing", session.selectedListingId()));
    }

    private void claimOneChunk(Player player, MenuSession session) {
        runLocked(player, session, () -> {
            if (session.selectedClaimId() == null) {
                player.sendRichMessage("<yellow>No claim is selected.</yellow>");
                unlockAndRefresh(player);
                return;
            }
            ClaimItemResult result = claimService.claimOneChunk(player, session.selectedClaimId());
            if (result.success()) menuController.invalidation().markClaimStateChanged();
            sendClaimItemResult(player, result);
            unlockAfterClaim(player, result);
        }, resourceKey("item-claim", session.selectedClaimId()));
    }

    private void claimAsMuchAsFits(Player player, MenuSession session) {
        runLocked(player, session, () -> {
            if (session.selectedClaimId() == null) {
                player.sendRichMessage("<yellow>No claim is selected.</yellow>");
                unlockAndRefresh(player);
                return;
            }
            ClaimItemResult result = claimService.claimAsMuchAsFits(player, session.selectedClaimId());
            if (result.success()) menuController.invalidation().markClaimStateChanged();
            sendClaimItemResult(player, result);
            unlockAfterClaim(player, result);
        }, resourceKey("item-claim", session.selectedClaimId()));
    }

    private void claimEarnings(Player player, MenuSession session) {
        runLocked(player, session, () -> {
            ClaimMoneyResult result = claimService.claimEarnings(player);
            if (!result.success()) {
                player.sendRichMessage("<red>Earnings claim failed:</red> <gray>" + escapeMini(result.failureMessage()) + "</gray>");
            } else if (result.empty()) {
                player.sendRichMessage("<yellow>You do not have pending earnings.</yellow>");
            } else {
                menuController.invalidation().markClaimStateChanged();
                player.sendRichMessage("<green>Claimed earnings:</green> <yellow>$" + formatMoney(result.claimedAmount()) + "</yellow>");
            }
            unlockAndRefresh(player);
        }, resourceKey("money-claim", player.getUniqueId()));
    }


    private void sendClaimItemResult(Player player, ClaimItemResult result) {
        if (!result.success()) {
            player.sendRichMessage("<red>Claim failed:</red> <gray>" + escapeMini(result.failureMessage()) + "</gray>");
            return;
        }

        String displayName = result.marketDisplayName() == null || result.marketDisplayName().isBlank()
                ? "Item"
                : result.marketDisplayName();
        player.sendRichMessage("<green>Claimed</green> <white>" + escapeMini(displayName) + "</white> <gray>x" + result.claimedAmount() + ".</gray>");
        if (result.remainingAmount() > 0) {
            player.sendRichMessage("<dark_gray>Remaining in claim storage: " + result.remainingAmount() + ".</dark_gray>");
        }
    }

    private void unlockAfterClaim(Player player, ClaimItemResult result) {
        if (result.success() && result.claimRemoved()) {
            MenuSession current = sessionManager.getOrCreate(player.getUniqueId()).withActionLocked(false);
            menuController.open(player, current.backOrMain());
            return;
        }
        unlockAndRefresh(player);
    }

    private String formatMoney(long hundredths) {
        return String.format(java.util.Locale.US, "%.2f", hundredths / 100.0);
    }

    private void runLocked(Player player, MenuSession session, Runnable action, String... resourceKeys) {
        var permit = actionGate.tryAcquire(player.getUniqueId(), resourceKeys);
        if (permit.isEmpty()) {
            player.sendRichMessage("<yellow>That market action is already being processed.</yellow>");
            return;
        }

        sessionManager.save(session.withActionLocked(true));
        try {
            action.run();
        } catch (RuntimeException exception) {
            player.sendRichMessage("<red>Market action failed:</red> <gray>" + escapeMini(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()) + "</gray>");
            unlockAndRefresh(player);
        } finally {
            MenuSession current = sessionManager.getOrCreate(player.getUniqueId());
            if (current.actionLocked()) {
                sessionManager.save(current.withActionLocked(false));
            }
            permit.get().close();
        }
    }

    private String resourceKey(String type, UUID id) {
        return id == null ? null : type + ":" + id;
    }

    private void unlockAndRefresh(Player player) {
        MenuSession current = sessionManager.getOrCreate(player.getUniqueId()).withActionLocked(false);
        menuController.open(player, current);
    }

    private String escapeMini(String input) {
        return input == null ? "" : input.replace("<", "\\<").replace(">", "\\>");
    }
}
