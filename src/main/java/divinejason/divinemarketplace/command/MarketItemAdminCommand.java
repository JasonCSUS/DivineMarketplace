package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market item admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class MarketItemAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketItemAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        String normalized = root.toLowerCase(Locale.ROOT);
        return normalized.equals("sort") || normalized.equals("define");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "sort" -> handleSort(sender, args);
            case "define" -> handleDefine(sender, args);
            default -> sender.sendRichMessage("<red>Unknown item admin subcommand.</red>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "sort" -> suggestSort(args);
            case "define" -> List.of();
            default -> List.of();
        };
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.item.sort")) {
            suggestions.add("sort");
        }
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.item.define")) {
            suggestions.add("define");
        }
        return suggestions;
    }

    private void handleSort(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.item.sort");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market sort &lt;item-or-marketKey&gt; &lt;category&gt;</gray>");
            return;
        }
        String categoryId = args[args.length - 1];
        if (!context.marketIndexService.getCategoryIds(true).contains(categoryId)) {
            sender.sendRichMessage("<red>Unknown category:</red> <gray>" + context.escapeMini(categoryId) + "</gray>");
            return;
        }
        String token = context.joinArgs(args, 1, args.length - 1);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market matched that token.</red>");
            return;
        }
        if (!context.marketIndexService.updateCategory(marketKey, categoryId)) {
            sender.sendRichMessage("<red>That market entry could not be recategorized.</red>");
            return;
        }
        context.plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>Moved</green> <white>" + context.escapeMini(marketKey) + "</white> <green>to category</green> <white>" + context.escapeMini(categoryId) + "</white>");
    }

    private void handleDefine(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.item.define");
        Player player = context.requirePlayer(sender);
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult inspection = context.customItemTypeExtractor.inspect(held);
        if (inspection == null || !inspection.hasCustomSignal()) {
            sender.sendRichMessage("<red>The held item did not resolve to a definable custom signal.</red>");
            return;
        }
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market define &lt;marketDisplayName&gt;</gray>");
            return;
        }
        String newDisplayName = context.joinArgs(args, 1, args.length);
        String categoryId = context.marketIndexService.getByMarketKey(inspection.itemType()) != null
                ? context.marketIndexService.getByMarketKey(inspection.itemType()).categoryId()
                : "unsorted";
        CustomItemDefinition updated = new CustomItemDefinition(
                inspection.itemType(),
                inspection.requiredMaterial(),
                inspection.requiredCustomModelData(),
                newDisplayName,
                categoryId,
                CustomItemDefinitionState.CONFIRMED
        );
        context.customItemRegistry.upsertDefinition(updated);
        context.plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>Defined custom item</green> <white>" + context.escapeMini(updated.itemType()) + "</white> <green>as</green> <white>" + context.escapeMini(updated.marketDisplayName()) + "</white>");
    }

    private Collection<String> suggestSort(String[] args) {
        if (args.length == 2) {
            return context.filterByPrefix(context.marketIndexService.getAdminTokens(), context.currentToken(args));
        }
        if (args.length == 3) {
            return context.filterByPrefix(context.marketIndexService.getCategoryIds(true), context.currentToken(args));
        }
        return List.of();
    }
}
