package divinejason.divinemarketplace.command;


/*
 * File role: Handles player-facing market history command subcommands and translates service results into chat/GUI feedback.
 */
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.menu.MenuSession;
import divinejason.divinemarketplace.menu.MenuView;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

final class MarketHistoryCommandHandler implements MarketPlayerCommandHandler {
    private final MarketPlayerCommandContext context;

    MarketHistoryCommandHandler(MarketPlayerCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRootToken(String rootToken) {
        return "history".equals(rootToken) || "pricehistory".equals(rootToken);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if ("history".equalsIgnoreCase(args[0])) {
            handleHistory(sender, args);
            return;
        }
        handlePriceHistory(sender, args);
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return context.filterByPrefix(context.marketIndexService.getPlayerDisplayNames(), context.currentToken(args));
        }
        return List.of();
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market history &lt;marketdisplayname&gt; [menu]</gray>");
            return;
        }

        boolean menu = args[args.length - 1].equalsIgnoreCase("menu");
        String token = context.joinArgs(args, 1, menu ? args.length - 1 : args.length);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market matched that name.</red>");
            return;
        }
        if (menu) {
            openHistoryMenu(context.requirePlayer(sender), marketKey, MenuView.SALE_HISTORY);
            return;
        }

        List<SaleRecord> sales = context.historyService.getSaleHistory(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Sale History</gold> <gray>-</gray> <white>" + context.escapeMini(token) + "</white>");
        if (sales.isEmpty()) {
            sender.sendRichMessage("<gray>No recent sale history found.</gray>");
            return;
        }
        for (SaleRecord sale : sales) {
            sender.sendRichMessage("<gray>-</gray> <white>" + context.escapeMini(sale.marketDisplayName()) + "</white> <gray>x" + sale.amountPurchased() + "</gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(sale.unitPrice() / 100.0) + "</yellow>");
        }
    }

    private void openHistoryMenu(Player player, String marketKey, MenuView historyView) {
        MenuSession root = MenuSession.create(player.getUniqueId());
        MenuSession listings = root.pushAndOpen(root
                .withView(MenuView.LISTING_BROWSER)
                .withSelectedMarketKey(marketKey)
                .withPage(0));
        context.menuController.open(player, listings.pushAndOpen(listings
                .withView(historyView)
                .withSelectedPriceHistoryMonth(null)
                .withPage(0)));
    }

    private void handlePriceHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market pricehistory &lt;marketdisplayname&gt; [menu]</gray>");
            return;
        }

        boolean menu = args[args.length - 1].equalsIgnoreCase("menu");
        String token = context.joinArgs(args, 1, menu ? args.length - 1 : args.length);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market matched that name.</red>");
            return;
        }
        if (!context.historyService.isPriceHistoryEnabled(marketKey)) {
            sender.sendRichMessage("<yellow>Price history is disabled for that market.</yellow>");
            return;
        }
        if (menu) {
            openHistoryMenu(context.requirePlayer(sender), marketKey, MenuView.PRICE_HISTORY);
            return;
        }

        List<YearMonth> months = context.historyService.getPriceHistoryMonths(marketKey);
        sender.sendRichMessage("<gold>Price History</gold> <gray>-</gray> <white>" + context.escapeMini(token) + "</white>");
        if (months.isEmpty()) {
            sender.sendRichMessage("<gray>No daily recommended-price history found.</gray>");
            return;
        }

        YearMonth month = months.get(0);
        List<RecommendationHistoryPoint> points = context.historyService.getPriceHistory(marketKey, month);
        sender.sendRichMessage("<dark_gray>Showing latest data month:</dark_gray> <gray>" + month + "</gray>");

        long min = Long.MAX_VALUE;
        long max = 0L;
        long total = 0L;
        for (RecommendationHistoryPoint point : points) {
            min = Math.min(min, point.recommendedUnitPrice());
            max = Math.max(max, point.recommendedUnitPrice());
            total += point.recommendedUnitPrice();

            String day = Instant.ofEpochMilli(point.recordedAtEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().format(MarketPlayerCommandContext.DAY_FORMAT);
            sender.sendRichMessage("<gray>-</gray> <white>" + day + "</white> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(point.recommendedUnitPrice() / 100.0) + "</yellow>");
        }

        double average = total / (double) points.size();
        sender.sendRichMessage("<dark_gray>Min:</dark_gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(min / 100.0) + "</yellow> <dark_gray>Max:</dark_gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(max / 100.0) + "</yellow> <dark_gray>Avg:</dark_gray> <yellow>$" + MarketPlayerCommandContext.MONEY_FORMAT.format(average / 100.0) + "</yellow>");
    }
}
