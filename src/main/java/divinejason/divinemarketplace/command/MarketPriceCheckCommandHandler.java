package divinejason.divinemarketplace.command;


/*
 * File role: Handles player-facing market price check command subcommands and translates service results into chat/GUI feedback.
 */
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/** Player-facing held-item recommendation lookup. */
final class MarketPriceCheckCommandHandler implements MarketPlayerCommandHandler {
    private final MarketPlayerCommandContext context;

    MarketPriceCheckCommandHandler(MarketPlayerCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRootToken(String rootToken) {
        return "pricecheck".equals(rootToken) || "pc".equals(rootToken);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = context.requirePlayer(sender);
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType().isAir() || heldItem.getAmount() <= 0) {
            sender.sendRichMessage("<red>Hold an item in your main hand to pricecheck it.</red>");
            return;
        }

        ResolvedItemDefinition definition;
        try {
            definition = context.itemIdentityResolver.resolve(heldItem);
        } catch (RuntimeException exception) {
            sender.sendRichMessage("<red>Could not resolve that item:</red> <gray>" + context.escapeMini(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()) + "</gray>");
            return;
        }

        String displayName = context.escapeMini(definition.marketDisplayName());

        if (!definition.recommendationEnabled()) {
            sender.sendRichMessage("<white>" + displayName + "</white> <yellow>is excluded from recommendation pricing.</yellow>");
            return;
        }

        long recommended = context.priceRecommendationService.getRecommendedUnitPrice(definition.marketKey());
        if (recommended <= 0L) {
            sender.sendRichMessage("<white>" + displayName + "</white> <yellow>does not have a learned recommended price yet.</yellow> <gray>You can still choose your own listing price.</gray>");
            return;
        }

        long stackEstimate = recommended * Math.max(1, heldItem.getAmount());
        sender.sendRichMessage("<white>" + displayName + "</white> <gray>has a recommended unit price of</gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(recommended / 100.0) + "</yellow><gray>.</gray>");
        if (heldItem.getAmount() > 1) {
            sender.sendRichMessage("<gray>Your held stack of x" + heldItem.getAmount() + " is estimated at</gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(stackEstimate / 100.0) + "</yellow><gray>.</gray>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }
}
