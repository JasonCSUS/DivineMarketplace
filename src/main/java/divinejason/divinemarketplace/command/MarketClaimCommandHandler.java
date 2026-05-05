package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.auction.model.ClaimMoneyResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

final class MarketClaimCommandHandler implements MarketPlayerCommandHandler {
    private final MarketPlayerCommandContext context;

    MarketClaimCommandHandler(MarketPlayerCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRootToken(String rootToken) {
        return "claim".equals(rootToken);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = context.requirePlayer(sender);
        if (args.length == 1) {
            sender.sendRichMessage("<yellow>Use /market to open the claims GUI, or /market claim earnings to claim money.</yellow>");
            return;
        }
        if (args[1].equalsIgnoreCase("earnings")) {
            ClaimMoneyResult result = context.claimService.claimEarnings(player);
            if (!result.success()) {
                sender.sendRichMessage("<red>Earnings claim failed:</red> <gray>" + context.escapeMini(result.failureMessage()) + "</gray>");
            } else if (result.empty()) {
                sender.sendRichMessage("<yellow>You do not have pending earnings.</yellow>");
            } else {
                sender.sendRichMessage("<green>Claimed earnings:</green> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(result.claimedAmount() / 100.0) + "</yellow>");
            }
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market claim</gray> <yellow>or</yellow> <gray>/market claim earnings</gray>");
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        return args.length == 2 ? context.filterByPrefix(List.of("earnings"), context.currentToken(args)) : List.of();
    }
}
