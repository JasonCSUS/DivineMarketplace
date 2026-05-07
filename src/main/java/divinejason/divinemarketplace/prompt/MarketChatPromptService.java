package divinejason.divinemarketplace.prompt;


/*
 * File role: Manages short-lived per-player chat prompts for search, listing, and relisting, then returns players to the correct GUI state.
 */
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.service.claim.ClaimService;
import divinejason.divinemarketplace.auction.service.listing.ListingService;
import divinejason.divinemarketplace.menu.MenuController;
import divinejason.divinemarketplace.menu.MenuDataFacade;
import divinejason.divinemarketplace.menu.MenuSession;
import divinejason.divinemarketplace.menu.MenuView;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles short-lived per-player chat prompts for market actions that need free-form numbers.
 *
 * Prompt state is intentionally separate from MenuSession because inventories are closed while
 * the player types, and menu close cleanup should remain safe for every player using the GUI.
 */
public final class MarketChatPromptService implements Listener {
    private static final long PROMPT_TIMEOUT_TICKS = 20L * 60L;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");

    private final JavaPlugin plugin;
    private final ListingService listingService;
    private final ClaimService claimService;
    private final MenuController menuController;
    private final MenuDataFacade dataFacade;
    private final ConcurrentMap<UUID, PendingPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public MarketChatPromptService(
            JavaPlugin plugin,
            ListingService listingService,
            ClaimService claimService,
            MenuController menuController,
            MenuDataFacade dataFacade
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.listingService = Objects.requireNonNull(listingService, "listingService");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.menuController = Objects.requireNonNull(menuController, "menuController");
        this.dataFacade = Objects.requireNonNull(dataFacade, "dataFacade");
    }

    public void promptListing(Player player) {
        promptListing(player, null);
    }

    public void promptListing(Player player, MenuSession returnOnCancel) {
        UUID playerUuid = player.getUniqueId();
        PendingPrompt prompt = PendingPrompt.listing(
                playerUuid,
                returnOnCancel,
                myListingsSession(playerUuid)
        );
        beginPrompt(player, prompt);
        player.sendRichMessage("<gold>Listing prompt:</gold> <gray>Type quantity and unit price.</gray>");
        sendUsage(player);
        player.sendRichMessage("<dark_gray>Price is per item. Keep the item in your main hand until you submit.</dark_gray>");
    }

    public void promptRelistClaim(Player player, UUID claimId, MenuSession returnOnCancel) {
        ItemClaimRecord claim = dataFacade.getPlayerItemClaimById(player.getUniqueId(), claimId);
        if (claim == null) {
            player.sendRichMessage("<yellow>That claim is no longer available.</yellow>");
            menuController.refresh(player);
            return;
        }

        PendingPrompt prompt = PendingPrompt.relistClaim(
                player.getUniqueId(),
                claimId,
                returnOnCancel == null ? claimDetailSession(player.getUniqueId(), claimId) : returnOnCancel,
                claimDetailSession(player.getUniqueId(), claimId)
        );
        beginPrompt(player, prompt);
        player.sendRichMessage("<gold>Relist prompt:</gold> <gray>Type quantity and unit price.</gray>");
        player.sendRichMessage("<gray>Available claim amount:</gray> <white>" + claim.amount() + "</white>");
        sendUsage(player);
    }

    public void promptSearch(Player player) {
        promptSearch(player, null);
    }

    public void promptSearch(Player player, MenuSession returnOnCancel) {
        UUID playerUuid = player.getUniqueId();
        PendingPrompt prompt = PendingPrompt.search(
                playerUuid,
                returnOnCancel,
                null
        );
        beginPrompt(player, prompt);
        player.sendRichMessage("<gold>Search prompt:</gold> <gray>Type search text, or type cancel to return.</gray>");
        player.sendRichMessage("<dark_gray>Example: diamond sword</dark_gray>");
    }

    public void clearPlayer(UUID playerUuid) {
        pendingPrompts.remove(playerUuid);
    }

    public void clearAll() {
        pendingPrompts.clear();
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingPrompt prompt = pendingPrompts.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> handlePromptInput(player, prompt.promptId(), message));
    }

    private void beginPrompt(Player player, PendingPrompt prompt) {
        pendingPrompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> expireIfStillPending(prompt), PROMPT_TIMEOUT_TICKS);
    }

    private void expireIfStillPending(PendingPrompt prompt) {
        if (!pendingPrompts.remove(prompt.playerUuid(), prompt)) {
            return;
        }

        Player player = plugin.getServer().getPlayer(prompt.playerUuid());
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendRichMessage("<yellow>Market chat prompt expired.</yellow>");
        reopen(player, prompt.returnOnCancel());
    }

    private void handlePromptInput(Player player, UUID promptId, String rawMessage) {
        PendingPrompt prompt = pendingPrompts.get(player.getUniqueId());
        if (prompt == null || !prompt.promptId().equals(promptId)) {
            return;
        }

        if (rawMessage.equalsIgnoreCase("cancel")) {
            pendingPrompts.remove(player.getUniqueId(), prompt);
            player.sendRichMessage("<yellow>Market prompt cancelled.</yellow>");
            reopen(player, prompt.returnOnCancel());
            return;
        }

        switch (prompt.type()) {
            case LIST_MAIN_HAND -> handleListingInput(player, prompt, rawMessage);
            case RELIST_CLAIM -> handleRelistInput(player, prompt, rawMessage);
            case SEARCH -> handleSearchInput(player, prompt, rawMessage);
        }
    }

    private void handleListingInput(Player player, PendingPrompt prompt, String rawMessage) {
        ItemStack sourceItem = player.getInventory().getItemInMainHand();
        if (sourceItem == null || sourceItem.getType().isAir() || sourceItem.getAmount() <= 0) {
            player.sendRichMessage("<red>Hold the item you want to list in your main hand, then try again.</red>");
            sendUsage(player);
            return;
        }

        ParsedPromptInput input;
        try {
            input = parseQuantityAndPrice(rawMessage, sourceItem.getAmount(), sourceItem.getMaxStackSize());
        } catch (PromptInputException exception) {
            player.sendRichMessage("<red>Invalid listing input:</red> <gray>" + escapeMini(exception.getMessage()) + "</gray>");
            sendUsage(player);
            return;
        }

        ListingCreateResult result = listingService.createOrMergeListing(player, sourceItem.clone(), input.quantity(), input.unitPriceHundredths());
        if (!result.success()) {
            player.sendRichMessage("<red>Listing failed:</red> <gray>" + escapeMini(result.failureReason() == null ? result.debugMessage() : result.failureReason().name()) + "</gray>");
            player.sendRichMessage("<dark_gray>Adjust the quantity/price and try again, or type cancel.</dark_gray>");
            return;
        }

        pendingPrompts.remove(player.getUniqueId(), prompt);
        menuController.invalidation().markListingInventoryChanged();
        sendListingSuccess(player, result, input.unitPriceHundredths());
        reopen(player, prompt.returnOnSuccess());
    }

    private void handleRelistInput(Player player, PendingPrompt prompt, String rawMessage) {
        ItemClaimRecord claim = dataFacade.getPlayerItemClaimById(player.getUniqueId(), prompt.claimId());
        if (claim == null) {
            pendingPrompts.remove(player.getUniqueId(), prompt);
            player.sendRichMessage("<yellow>That claim is no longer available.</yellow>");
            reopen(player, claimsSession(player.getUniqueId()));
            return;
        }

        ParsedPromptInput input;
        try {
            input = parseQuantityAndPrice(rawMessage, claim.amount(), claim.claimItemSnapshot().getMaxStackSize());
        } catch (PromptInputException exception) {
            player.sendRichMessage("<red>Invalid relist input:</red> <gray>" + escapeMini(exception.getMessage()) + "</gray>");
            player.sendRichMessage("<gray>Available claim amount:</gray> <white>" + claim.amount() + "</white>");
            sendUsage(player);
            return;
        }

        ListingCreateResult result = claimService.relistClaim(player, claim.claimId(), input.quantity(), input.unitPriceHundredths());
        if (!result.success()) {
            player.sendRichMessage("<red>Relist failed:</red> <gray>" + escapeMini(result.failureReason() == null ? result.debugMessage() : result.failureReason().name()) + "</gray>");
            player.sendRichMessage("<dark_gray>Adjust the quantity/price and try again, or type cancel.</dark_gray>");
            return;
        }

        pendingPrompts.remove(player.getUniqueId(), prompt);
        menuController.invalidation().markListingAndClaimsChanged();
        sendRelistSuccess(player, result, input.unitPriceHundredths());

        ItemClaimRecord remainingClaim = dataFacade.getPlayerItemClaimById(player.getUniqueId(), claim.claimId());
        reopen(player, remainingClaim == null ? claimsSession(player.getUniqueId()) : claimDetailSession(player.getUniqueId(), claim.claimId()));
    }

    private void handleSearchInput(Player player, PendingPrompt prompt, String rawMessage) {
        String query = rawMessage == null ? "" : rawMessage.trim();
        if (query.isBlank()) {
            player.sendRichMessage("<red>Search text cannot be empty.</red> <dark_gray>Type cancel to return.</dark_gray>");
            return;
        }

        pendingPrompts.remove(player.getUniqueId(), prompt);
        UUID playerUuid = player.getUniqueId();
        MenuSession base = prompt.returnOnCancel() == null ? MenuSession.create(playerUuid) : prompt.returnOnCancel();
        reopen(player, base.pushAndOpen(base
                .withView(MenuView.SEARCH_RESULTS)
                .withSearchQuery(query)
                .withPage(0)
                .withActionLocked(false)));
    }

    private ParsedPromptInput parseQuantityAndPrice(String rawMessage, int availableAmount, int maxStackSize) throws PromptInputException {
        String[] parts = rawMessage.trim().split("\\s+");
        if (parts.length != 2) {
            throw new PromptInputException("Use exactly two values: quantity and unit price.");
        }

        int quantity = parseQuantity(parts[0], availableAmount, maxStackSize);
        long unitPrice = parseMoneyToHundredths(parts[1]);
        if (unitPrice <= 0L) {
            throw new PromptInputException("Unit price must be greater than zero.");
        }
        return new ParsedPromptInput(quantity, unitPrice);
    }

    private int parseQuantity(String rawQuantity, int availableAmount, int maxStackSize) throws PromptInputException {
        int safeAvailable = Math.max(0, availableAmount);
        if (safeAvailable <= 0) {
            throw new PromptInputException("There is no available quantity to list.");
        }

        String normalized = rawQuantity.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized)) {
            return safeAvailable;
        }
        if ("stack".equals(normalized)) {
            return Math.min(safeAvailable, Math.max(1, maxStackSize));
        }

        int quantity;
        try {
            quantity = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new PromptInputException("Quantity must be a number, all, or stack.");
        }
        if (quantity <= 0) {
            throw new PromptInputException("Quantity must be greater than zero.");
        }
        if (quantity > safeAvailable) {
            throw new PromptInputException("Quantity cannot exceed the available amount of " + safeAvailable + ".");
        }
        return quantity;
    }

    private long parseMoneyToHundredths(String rawPrice) throws PromptInputException {
        String cleaned = rawPrice.trim().replace("$", "").replace(",", "");
        if (cleaned.isBlank()) {
            throw new PromptInputException("Unit price is empty.");
        }
        try {
            return new BigDecimal(cleaned)
                    .setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new PromptInputException("Unit price must be a positive number with at most two decimals.");
        }
    }

    private void sendUsage(Player player) {
        player.sendRichMessage("<gray>Examples:</gray> <white>32 150</white><gray>,</gray> <white>stack 150</white><gray>,</gray> <white>all 150</white>");
        player.sendRichMessage("<dark_gray>Type cancel to return.</dark_gray>");
    }

    private void sendListingSuccess(Player player, ListingCreateResult result, long unitPriceHundredths) {
        String actionWord = result.mergedIntoExisting() ? "Merged listing" : "Created listing";
        player.sendRichMessage("<green>" + actionWord + ".</green> <white>" + escapeMini(result.marketDisplayName()) + "</white> <gray>x" + result.actualQuantity() + "</gray> <yellow>@ $" + MONEY_FORMAT.format(unitPriceHundredths / 100.0) + "</yellow>");
    }

    private void sendRelistSuccess(Player player, ListingCreateResult result, long unitPriceHundredths) {
        String actionWord = result.mergedIntoExisting() ? "Merged relisted claim" : "Relisted claim";
        player.sendRichMessage("<green>" + actionWord + ".</green> <white>" + escapeMini(result.marketDisplayName()) + "</white> <gray>x" + result.actualQuantity() + "</gray> <yellow>@ $" + MONEY_FORMAT.format(unitPriceHundredths / 100.0) + "</yellow>");
    }

    private void reopen(Player player, MenuSession session) {
        if (session != null && player.isOnline()) {
            menuController.open(player, session.withActionLocked(false));
        }
    }

    private MenuSession myListingsSession(UUID playerUuid) {
        MenuSession root = MenuSession.create(playerUuid);
        return root.pushAndOpen(root.withView(MenuView.MY_LISTINGS).withPage(0));
    }

    private MenuSession claimsSession(UUID playerUuid) {
        MenuSession root = MenuSession.create(playerUuid);
        return root.pushAndOpen(root.withView(MenuView.CLAIMS).withPage(0));
    }

    private MenuSession claimDetailSession(UUID playerUuid, UUID claimId) {
        MenuSession claims = claimsSession(playerUuid);
        return claims.pushAndOpen(claims.withView(MenuView.CLAIM_DETAIL).withSelectedClaimId(claimId).withPage(0));
    }

    private String escapeMini(String input) {
        return input == null ? "" : input.replace("<", "\\<").replace(">", "\\>");
    }

    private record ParsedPromptInput(int quantity, long unitPriceHundredths) {}

    private record PendingPrompt(
            UUID promptId,
            UUID playerUuid,
            PromptType type,
            UUID claimId,
            MenuSession returnOnCancel,
            MenuSession returnOnSuccess
    ) {
        static PendingPrompt listing(UUID playerUuid, MenuSession returnOnCancel, MenuSession returnOnSuccess) {
            return new PendingPrompt(UUID.randomUUID(), playerUuid, PromptType.LIST_MAIN_HAND, null, returnOnCancel, returnOnSuccess);
        }

        static PendingPrompt relistClaim(UUID playerUuid, UUID claimId, MenuSession returnOnCancel, MenuSession returnOnSuccess) {
            return new PendingPrompt(UUID.randomUUID(), playerUuid, PromptType.RELIST_CLAIM, claimId, returnOnCancel, returnOnSuccess);
        }

        static PendingPrompt search(UUID playerUuid, MenuSession returnOnCancel, MenuSession returnOnSuccess) {
            return new PendingPrompt(UUID.randomUUID(), playerUuid, PromptType.SEARCH, null, returnOnCancel, returnOnSuccess);
        }
    }

    private enum PromptType {
        LIST_MAIN_HAND,
        RELIST_CLAIM,
        SEARCH
    }

    private static final class PromptInputException extends Exception {
        private PromptInputException(String message) {
            super(message);
        }
    }
}
