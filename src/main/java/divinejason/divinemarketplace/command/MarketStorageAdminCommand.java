package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market storage admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import divinejason.divinemarketplace.DivineMarketplace.SQLiteStorageMaintenanceResult;
import divinejason.divinemarketplace.config.ConfigService;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Admin storage diagnostics and manual SQLite cleanup entry point.
 *
 * SQLite uses one shared database plus WAL/SHM sidecar files, so this command
 * reports two kinds of pressure:
 * - aggregate on-disk size for market.db + market.db-wal + market.db-shm
 * - logical per-table payload size for the history/claim tables with configured limits
 */
final class MarketStorageAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketStorageAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        return "storage".equals(root);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.storage");
        if (args.length >= 2 && "cleanup".equalsIgnoreCase(args[1])) {
            runCleanup(sender);
            return;
        }
        showStorage(sender);
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        return context.hasAdminPermission(sender, "divinemarketplace.admin.storage") ? List.of("storage") : List.of();
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (!context.hasAdminPermission(sender, "divinemarketplace.admin.storage")) {
            return List.of();
        }
        if (args.length == 2) {
            return context.filterByPrefix(List.of("cleanup"), context.currentToken(args));
        }
        return List.of();
    }

    private void showStorage(CommandSender sender) {
        sender.sendRichMessage("<gold>DivineMarketplace Storage</gold>");
        sender.sendRichMessage("<gray>SQLite files:</gray> <white>" + formatBytes(context.plugin.getSQLiteStorageBytes()) + "</white>");
        sender.sendRichMessage(row("Sale history", context.plugin.getSalesHistoryPayloadBytes(), ConfigService.get().salesHistoryMaxBytes()));
        sender.sendRichMessage(row("Admin sales", context.plugin.getAdminSalesHistoryPayloadBytes(), ConfigService.get().adminSalesHistoryMaxBytes()));
        sender.sendRichMessage(row("Admin listings", context.plugin.getAdminListingsHistoryPayloadBytes(), ConfigService.get().adminListingsHistoryMaxBytes()));
        sender.sendRichMessage(row("Admin claims", context.plugin.getAdminClaimsHistoryPayloadBytes(), ConfigService.get().adminClaimsHistoryMaxBytes()));
        sender.sendRichMessage(row("Item claims", context.plugin.getItemClaimPayloadBytes(), ConfigService.get().itemClaimsSoftMaxBytes()));
        sender.sendRichMessage("<dark_gray>Run /market storage cleanup to apply retention cleanup now.</dark_gray>");
    }

    private void runCleanup(CommandSender sender) {
        SQLiteStorageMaintenanceResult result = context.plugin.runSQLiteStorageMaintenance();
        sender.sendRichMessage("<green>SQLite storage maintenance complete.</green>");
        sender.sendRichMessage("<gray>Storage:</gray> <white>" + formatBytes(result.beforeBytes()) + " -> " + formatBytes(result.afterBytes()) + "</white>");
        sender.sendRichMessage("<gray>Removed:</gray> <white>" + result.totalPurged() + " record(s)</white> "
                + "<dark_gray>(sales=" + result.purgedSales()
                + ", admin_sales=" + result.purgedAdminSales()
                + ", admin_listings=" + result.purgedAdminListings()
                + ", admin_claims=" + result.purgedAdminClaims()
                + ", abandoned_item_claims=" + result.purgedItemClaims() + ")</dark_gray>");
    }

    private String row(String label, long usedBytes, long maxBytes) {
        String max = maxBytes <= 0L ? "disabled" : formatBytes(maxBytes);
        return "<gray>" + context.escapeMini(label) + ":</gray> <white>" + formatBytes(usedBytes) + "</white> <dark_gray>/ " + max + "</dark_gray>";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024.0) {
            return String.format(Locale.US, "%.1f KiB", kib);
        }
        double mib = kib / 1024.0;
        if (mib < 1024.0) {
            return String.format(Locale.US, "%.2f MiB", mib);
        }
        return String.format(Locale.US, "%.2f GiB", mib / 1024.0);
    }
}
