package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.persistence.BinaryListingStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-side /market subcommand helper.
 *
 * Current implementation strategy:
 * - fully wires low-risk history inspection/export commands
 * - keeps unfinished admin maintenance commands friendly and explicit instead of
 *   pretending they are finished
 * - exposes market keys and market display names in suggestions for admin UX
 */
public final class MarketAdminCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");

    private final DivineMarketplace plugin;
    private final BinaryListingStore listingStore;
    private final DefaultAdminHistoryService adminHistoryService;
    private final AdminHistoryExportService adminHistoryExportService;

    public MarketAdminCommand(
            DivineMarketplace plugin,
            BinaryListingStore listingStore,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService
    ) {
        this.plugin = plugin;
        this.listingStore = listingStore;
        this.adminHistoryService = adminHistoryService;
        this.adminHistoryExportService = adminHistoryExportService;
    }

    public boolean handlesRootToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return normalized.equals("reload")
                || normalized.equals("recalc")
                || normalized.equals("setprice")
                || normalized.equals("playerhistory")
                || normalized.equals("audithistory")
                || normalized.equals("sort")
                || normalized.equals("define")
                || normalized.equals("enchant")
                || normalized.equals("admin");
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendRichMessage("<red>Missing admin subcommand.</red>");
            return;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "reload" -> handleReload(sender);
            case "recalc" -> handleRecalc(sender, args);
            case "setprice" -> handleSetPrice(sender, args);
            case "playerhistory" -> handlePlayerHistory(sender, args);
            case "audithistory" -> handleAuditHistory(sender, args);
            case "sort" -> handleSort(sender, args);
            case "define" -> handleDefine(sender, args);
            case "enchant" -> handleEnchant(sender, args);
            case "admin" -> handleAdmin(sender, args);
            default -> sender.sendRichMessage("<red>Unknown admin subcommand.</red>");
        }
    }

    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return filterByPrefix(rootSuggestions(sender), currentToken(args));
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "recalc" -> suggestRecalc(args);
            case "setprice" -> suggestSetPrice(args);
            case "playerhistory" -> suggestPlayerHistory(args);
            case "audithistory" -> suggestAuditHistory(args);
            case "sort" -> suggestSort(args);
            case "define" -> suggestDefine(args);
            case "enchant" -> suggestEnchant(args);
            case "admin" -> suggestAdmin(args);
            default -> List.of();
        };
    }

    private void handleReload(CommandSender sender) {
        require(sender, "divinemarketplace.admin.reload");
        sender.sendRichMessage("<yellow>/market reload is recognized, but real live reload wiring is not finished yet.</yellow>");
    }

    private void handleRecalc(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.recalc");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market recalc all</gray> <yellow>or</yellow> <gray>/market recalc &lt;marketKey/displayName&gt;</gray>");
            return;
        }
        sender.sendRichMessage("<yellow>Market recalculation command recognized, but recalculation service wiring is not finished yet.</yellow>");
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.price.set");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market setprice &lt;marketKey/displayName&gt; &lt;price&gt;</gray>");
            return;
        }
        sender.sendRichMessage("<yellow>Manual price override is recognized, but the price-setting service is not finished yet.</yellow>");
    }

    private void handlePlayerHistory(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.history.view");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market playerhistory &lt;player&gt; &lt;buy|sell|claim&gt; [export]</gray>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetUuid = target.getUniqueId();
        String historyType = args[2].toLowerCase(Locale.ROOT);
        boolean export = args.length >= 4 && args[3].equalsIgnoreCase("export");

        if (export) {
            sender.sendRichMessage("<yellow>Player-history export is not fully wired yet for split buy/sell/claim views.</yellow>");
        }

        List<AdminTransactionRecord> records = switch (historyType) {
            case "buy" -> adminHistoryService.getSaleHistoryByPlayer(targetUuid, 0, 50).stream()
                    .filter(record -> targetUuid.equals(record.buyerUuid()))
                    .limit(10)
                    .toList();
            case "sell" -> adminHistoryService.getListingHistoryByPlayer(targetUuid, 0, 50).stream()
                    .limit(10)
                    .toList();
            case "claim" -> adminHistoryService.getClaimHistoryByPlayer(targetUuid, 0, 50).stream()
                    .limit(10)
                    .toList();
            default -> null;
        };

        if (records == null) {
            sender.sendRichMessage("<red>History type must be buy, sell, or claim.</red>");
            return;
        }

        sender.sendRichMessage("<gold>Player History</gold> <gray>-</gray> <white>" + escapeMini(target.getName() != null ? target.getName() : args[1]) + "</white> <gray>(" + historyType + ")</gray>");
        if (records.isEmpty()) {
            sender.sendRichMessage("<gray>No matching records found.</gray>");
            return;
        }

        for (AdminTransactionRecord record : records) {
            sender.sendRichMessage(formatAdminRecord(record));
        }
    }

    private void handleAuditHistory(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.history.view");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market audithistory &lt;item-or-marketKey&gt; [export]</gray>");
            return;
        }

        boolean export = args[args.length - 1].equalsIgnoreCase("export");
        String token = joinArgs(args, 1, export ? args.length - 1 : args.length);
        String marketKey = resolveMarketKeyFromToken(token);

        if (marketKey == null) {
            sender.sendRichMessage("<red>No active market key matched that token.</red>");
            return;
        }

        if (export) {
            Path exportPath = adminHistoryExportService.exportSalesByMarketKey(marketKey);
            sender.sendRichMessage("<green>Export written:</green> <gray>" + escapeMini(exportPath.toAbsolutePath().toString()) + "</gray>");
            return;
        }

        List<AdminTransactionRecord> records = adminHistoryService.getSaleHistoryByMarketKey(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Audit History</gold> <gray>-</gray> <white>" + escapeMini(marketKey) + "</white>");
        if (records.isEmpty()) {
            sender.sendRichMessage("<gray>No audit sale records found for that market key.</gray>");
            return;
        }

        for (AdminTransactionRecord record : records) {
            sender.sendRichMessage(formatAdminRecord(record));
        }
    }

    private void handleSort(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.item.sort");
        sender.sendRichMessage("<yellow>/market sort is recognized, but category-edit command wiring is not finished yet.</yellow>");
    }

    private void handleDefine(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.item.define");
        sender.sendRichMessage("<yellow>/market define is recognized, but custom-definition edit command wiring is not finished yet.</yellow>");
    }

    private void handleEnchant(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.enchant.define");
        sender.sendRichMessage("<yellow>/market enchant define is recognized, but enchant-definition wiring is not finished yet.</yellow>");
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.flagged.view");
        if (args.length >= 2 && args[1].equalsIgnoreCase("flagged")) {
            sender.sendRichMessage("<yellow>Flagged-item view is recognized, but the flagged item service is not finished yet.</yellow>");
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market admin flagged</gray>");
    }

    private void require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission) && !sender.hasPermission("divinemarketplace.admin")) {
            throw new SecurityException("No permission: " + permission);
        }
    }

    private Collection<String> rootSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (sender.hasPermission("divinemarketplace.admin.reload") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("reload");
        }
        if (sender.hasPermission("divinemarketplace.admin.recalc") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("recalc");
        }
        if (sender.hasPermission("divinemarketplace.admin.price.set") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("setprice");
        }
        if (sender.hasPermission("divinemarketplace.admin.history.view") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("playerhistory");
            suggestions.add("audithistory");
        }
        if (sender.hasPermission("divinemarketplace.admin.item.sort") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("sort");
        }
        if (sender.hasPermission("divinemarketplace.admin.item.define") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("define");
        }
        if (sender.hasPermission("divinemarketplace.admin.enchant.define") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("enchant");
        }
        if (sender.hasPermission("divinemarketplace.admin.flagged.view") || sender.hasPermission("divinemarketplace.admin")) {
            suggestions.add("admin");
        }
        return suggestions;
    }

    private Collection<String> suggestRecalc(String[] args) {
        if (args.length == 2) {
            Set<String> tokens = new LinkedHashSet<>();
            tokens.add("all");
            tokens.addAll(collectAdminMarketTokens());
            return filterByPrefix(tokens, currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestSetPrice(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(collectAdminMarketTokens(), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestPlayerHistory(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).toList(), currentToken(args));
        }
        if (args.length == 3) {
            return filterByPrefix(List.of("buy", "sell", "claim"), currentToken(args));
        }
        if (args.length == 4) {
            return filterByPrefix(List.of("export"), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestAuditHistory(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(collectAdminMarketTokens(), currentToken(args));
        }
        if (args.length == 3) {
            return filterByPrefix(List.of("export"), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestSort(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(collectAdminMarketTokens(), currentToken(args));
        }
        if (args.length == 3) {
            return filterByPrefix(loadCategoryIds(), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestDefine(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(collectAdminMarketTokens(), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestEnchant(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(List.of("define"), currentToken(args));
        }
        return List.of();
    }

    private Collection<String> suggestAdmin(String[] args) {
        if (args.length == 2) {
            return filterByPrefix(List.of("flagged"), currentToken(args));
        }
        return List.of();
    }

    private Set<String> collectAdminMarketTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        for (var listing : listingStore.getAllActive()) {
            tokens.add(listing.marketKey());
            tokens.add(listing.marketDisplayName());
        }
        return tokens;
    }

    private List<String> loadCategoryIds() {
        return MarketCommand.loadCategoryIds(plugin);
    }

    private String resolveMarketKeyFromToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        for (var listing : listingStore.getAllActive()) {
            if (listing.marketKey().equalsIgnoreCase(token) || listing.marketDisplayName().equalsIgnoreCase(token)) {
                return listing.marketKey();
            }
        }
        for (var listing : listingStore.getAllActive()) {
            if (listing.marketKey().toLowerCase(Locale.ROOT).startsWith(normalized)
                    || listing.marketDisplayName().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                return listing.marketKey();
            }
        }
        return null;
    }

    private String formatAdminRecord(AdminTransactionRecord record) {
        return "<gray>-</gray> <white>" + escapeMini(record.marketDisplayName() == null ? "(no item)" : record.marketDisplayName())
                + "</white> <gray>x" + record.amount() + "</gray> <yellow>$" + MONEY_FORMAT.format(record.totalPrice() / 100.0) + "</yellow>"
                + " <dark_gray>[" + escapeMini(record.status() == null ? "?" : record.status()) + "]</dark_gray>";
    }

    private Collection<String> filterByPrefix(Collection<String> input, String currentToken) {
        String normalized = currentToken.toLowerCase(Locale.ROOT);
        return input.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    private String currentToken(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }

    private String joinArgs(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private String escapeMini(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
