package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market price admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MarketPriceAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketPriceAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        String normalized = root.toLowerCase(Locale.ROOT);
        return normalized.equals("reload") || normalized.equals("recalc") || normalized.equals("setprice");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "reload" -> handleReload(sender);
            case "recalc" -> handleRecalc(sender, args);
            case "setprice" -> handleSetPrice(sender, args);
            default -> sender.sendRichMessage("<red>Unknown price/admin subcommand.</red>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "recalc" -> suggestRecalc(args);
            case "setprice" -> suggestSetPrice(args);
            default -> List.of();
        };
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.reload")) {
            suggestions.add("reload");
        }
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.recalc")) {
            suggestions.add("recalc");
        }
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.price.set")) {
            suggestions.add("setprice");
        }
        return suggestions;
    }

    private void handleReload(CommandSender sender) {
        context.require(sender, "divinemarketplace.admin.reload");
        context.plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>DivineMarketplace runtime data reloaded.</green>");
    }

    private void handleRecalc(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.recalc");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market recalc all</gray> <yellow>or</yellow> <gray>/market recalc &lt;marketKey/displayName&gt;</gray>");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            context.marketRecalculationService.scheduleGlobalRecalculation();
            sender.sendRichMessage("<green>Scheduled global async market recalculation.</green>");
            return;
        }
        String token = context.joinArgs(args, 1, args.length);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market matched that token.</red>");
            return;
        }
        context.marketRecalculationService.scheduleMarketRecalculation(marketKey);
        sender.sendRichMessage("<green>Scheduled async recalculation for:</green> <white>" + context.escapeMini(marketKey) + "</white>");
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.price.set");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market setprice &lt;marketKey/displayName&gt; &lt;price&gt;</gray>");
            return;
        }
        long price;
        try {
            price = context.parseMoneyToHundredths(args[args.length - 1]);
        } catch (Exception exception) {
            sender.sendRichMessage("<red>Invalid price.</red>");
            return;
        }
        String token = context.joinArgs(args, 1, args.length - 1);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market matched that token.</red>");
            return;
        }
        context.priceRecommendationService.setManualRecommendedUnitPrice(marketKey, price);
        sender.sendRichMessage("<green>Set recommended price for</green> <white>" + context.escapeMini(marketKey)
                + "</white> <green>to</green> <yellow>$" + MarketAdminCommandContext.MONEY_FORMAT.format(price / 100.0) + "</yellow>");
    }

    private Collection<String> suggestRecalc(String[] args) {
        if (args.length == 2) {
            Set<String> tokens = new LinkedHashSet<>();
            tokens.add("all");
            tokens.addAll(context.marketIndexService.getAdminTokens());
            return context.filterByPrefix(tokens, context.currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestSetPrice(String[] args) {
        return args.length == 2
                ? context.filterByPrefix(context.marketIndexService.getAdminTokens(), context.currentToken(args))
                : List.of();
    }
}
