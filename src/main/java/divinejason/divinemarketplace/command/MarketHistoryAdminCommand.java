package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class MarketHistoryAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketHistoryAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        String normalized = root.toLowerCase(Locale.ROOT);
        return normalized.equals("playerhistory") || normalized.equals("audithistory");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "playerhistory" -> handlePlayerHistory(sender, args);
            case "audithistory" -> handleAuditHistory(sender, args);
            default -> sender.sendRichMessage("<red>Unknown history admin subcommand.</red>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "playerhistory" -> suggestPlayerHistory(args);
            case "audithistory" -> suggestAuditHistory(args);
            default -> List.of();
        };
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        if (!context.hasAdminPermission(sender, "divinemarketplace.admin.history.view")) {
            return List.of();
        }
        return List.of("playerhistory", "audithistory");
    }

    private void handlePlayerHistory(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.history.view");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market playerhistory &lt;player&gt; &lt;buy|sell|claim&gt; [export]</gray>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetUuid = target.getUniqueId();
        String historyType = args[2].toLowerCase(Locale.ROOT);
        boolean export = args.length >= 4 && args[3].equalsIgnoreCase("export");
        List<AdminTransactionRecord> records = switch (historyType) {
            case "buy" -> context.adminHistoryService.getSaleHistoryByPlayer(targetUuid, 0, 50).stream()
                    .filter(record -> targetUuid.equals(record.buyerUuid()))
                    .limit(50)
                    .toList();
            case "sell" -> context.adminHistoryService.getListingHistoryByPlayer(targetUuid, 0, 50).stream()
                    .limit(50)
                    .toList();
            case "claim" -> context.adminHistoryService.getClaimHistoryByPlayer(targetUuid, 0, 50).stream()
                    .limit(50)
                    .toList();
            default -> null;
        };
        if (records == null) {
            sender.sendRichMessage("<red>History type must be buy, sell, or claim.</red>");
            return;
        }
        if (export) {
            context.require(sender, "divinemarketplace.admin.history.export");
            String playerName = target.getName() != null ? target.getName() : args[1];
            Path exportPath = context.writeRecordExport(
                    "playerhistory_" + context.sanitizeFileToken(args[1]) + "_" + historyType,
                    "Player history for " + playerName + " (" + historyType + ")",
                    records
            );
            sender.sendRichMessage("<green>Export written:</green> <gray>" + context.escapeMini(exportPath.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        sender.sendRichMessage("<gold>Player History</gold> <gray>-</gray> <white>" + context.escapeMini(target.getName() != null ? target.getName() : args[1]) + "</white> <gray>(" + historyType + ")</gray>");
        if (records.isEmpty()) {
            sender.sendRichMessage("<gray>No matching records found.</gray>");
            return;
        }
        for (AdminTransactionRecord record : records) {
            sender.sendRichMessage(context.formatAdminRecord(record));
        }
    }

    private void handleAuditHistory(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.history.view");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market audithistory &lt;item-or-marketKey&gt; [export]</gray>");
            return;
        }
        boolean export = args[args.length - 1].equalsIgnoreCase("export");
        String token = context.joinArgs(args, 1, export ? args.length - 1 : args.length);
        String marketKey = context.marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No market key matched that token.</red>");
            return;
        }
        if (export) {
            context.require(sender, "divinemarketplace.admin.history.export");
            Path exportPath = context.adminHistoryExportService.exportSalesByMarketKey(marketKey);
            sender.sendRichMessage("<green>Export written:</green> <gray>" + context.escapeMini(exportPath.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        List<AdminTransactionRecord> records = context.adminHistoryService.getSaleHistoryByMarketKey(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Audit History</gold> <gray>-</gray> <white>" + context.escapeMini(marketKey) + "</white>");
        if (records.isEmpty()) {
            sender.sendRichMessage("<gray>No audit sale records found for that market key.</gray>");
            return;
        }
        for (AdminTransactionRecord record : records) {
            sender.sendRichMessage(context.formatAdminRecord(record));
        }
    }

    private Collection<String> suggestPlayerHistory(String[] args) {
        if (args.length == 2) {
            return context.filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), context.currentToken(args));
        }
        if (args.length == 3) {
            return context.filterByPrefix(List.of("buy", "sell", "claim"), context.currentToken(args));
        }
        if (args.length == 4) {
            return context.filterByPrefix(List.of("export"), context.currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestAuditHistory(String[] args) {
        if (args.length == 2) {
            return context.filterByPrefix(context.marketIndexService.getAdminTokens(), context.currentToken(args));
        }
        if (args.length == 3) {
            return context.filterByPrefix(List.of("export"), context.currentToken(args));
        }
        return List.of();
    }
}
