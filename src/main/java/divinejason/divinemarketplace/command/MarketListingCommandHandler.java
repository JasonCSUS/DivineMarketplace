package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class MarketListingCommandHandler implements MarketPlayerCommandHandler {
    private final MarketPlayerCommandContext context;

    MarketListingCommandHandler(MarketPlayerCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRootToken(String rootToken) {
        return "list".equals(rootToken);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = context.requirePlayer(sender);
        if (args.length == 1) {
            context.chatPromptService.promptListing(player);
            return;
        }
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market list</gray> <yellow>then type</yellow> <gray>&lt;quantity&gt; &lt;unitPrice&gt;</gray>");
            return;
        }

        int quantity;
        long unitPriceHundredths;
        try {
            quantity = Integer.parseInt(args[1]);
            unitPriceHundredths = context.parseMoneyToHundredths(args[2]);
        } catch (Exception exception) {
            sender.sendRichMessage("<red>Invalid quantity or unit price.</red>");
            return;
        }

        ItemStack sourceItem = player.getInventory().getItemInMainHand();
        ListingCreateResult result = context.listingService.createOrMergeListing(player, sourceItem, quantity, unitPriceHundredths);
        if (!result.success()) {
            sender.sendRichMessage("<red>Listing failed:</red> <gray>" + context.escapeMini(result.failureReason().name()) + "</gray> <dark_gray>(" + context.escapeMini(result.debugMessage() == null ? "" : result.debugMessage()) + ")</dark_gray>");
            return;
        }

        String actionWord = result.mergedIntoExisting() ? "Merged listing" : "Created listing";
        sender.sendRichMessage("<green>" + actionWord + ".</green> <white>" + context.escapeMini(result.marketDisplayName()) + "</white> <gray>x" + result.actualQuantity() + "</gray> <yellow>@ $" + MarketPlayerCommandContext.MONEY_FORMAT.format(unitPriceHundredths / 100.0) + "</yellow>");
    }
}
