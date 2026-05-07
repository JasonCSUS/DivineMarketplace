package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market custom override admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class MarketCustomOverrideAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketCustomOverrideAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        return root.equalsIgnoreCase("custom");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.custom.override");
        Player player = context.requirePlayer(sender);
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult result = context.customItemTypeExtractor.inspect(held);
        if (result == null || result.signature() == null || result.signature().isBlank()) {
            sender.sendRichMessage("<red>The held item did not produce a stable override signature.</red>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("vanilla")) {
            String note = args.length >= 3 ? context.joinArgs(args, 2, args.length) : "";
            context.overrideStore.putTreatAsVanilla(result.signature(), note);
            sender.sendRichMessage("<green>Marked held item signature as treat-as-vanilla.</green>");
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("removeoverride") || args[1].equalsIgnoreCase("clear"))) {
            context.overrideStore.delete(result.signature());
            sender.sendRichMessage("<green>Removed custom override for held item signature.</green>");
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market custom vanilla [note]</gray> <yellow>or</yellow> <gray>/market custom removeoverride</gray>");
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        return args.length == 2
                ? context.filterByPrefix(List.of("vanilla", "removeoverride"), context.currentToken(args))
                : List.of();
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        return context.hasAdminPermission(sender, "divinemarketplace.admin.custom.override") ? List.of("custom") : List.of();
    }
}
