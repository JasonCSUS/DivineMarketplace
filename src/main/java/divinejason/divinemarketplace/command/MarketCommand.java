package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.CategoryService;
import divinejason.divinemarketplace.auction.service.ClaimService;
import divinejason.divinemarketplace.auction.service.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.service.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.service.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.HistoryService;
import divinejason.divinemarketplace.auction.service.ListingService;
import divinejason.divinemarketplace.auction.service.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.PriceRecommendationService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@NullMarked
public final class MarketCommand implements BasicCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ListingService listingService;
    private final ClaimService claimService;
    private final SQLiteListingStore listingStore;
    private final HistoryService historyService;
    private final CategoryService categoryService;
    private final FlattenedMarketIndexService marketIndexService;
    private final PriceRecommendationService priceRecommendationService;
    private final MarketAdminCommand marketAdminCommand;

    public MarketCommand(
            DivineMarketplace plugin,
            ListingService listingService,
            ClaimService claimService,
            SQLiteListingStore listingStore,
            HistoryService historyService,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService,
            CategoryService categoryService,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            CustomItemRegistry customItemRegistry,
            MarketRecalculationService marketRecalculationService,
            SQLiteCustomEnchantStore customEnchantStore,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService customItemMetadataLogService,
            SQLiteCustomItemOverrideStore customItemOverrideStore,
            CustomItemCollisionLogService customItemCollisionLogService
    ) {
        this.listingService = listingService;
        this.claimService = claimService;
        this.listingStore = listingStore;
        this.historyService = historyService;
        this.categoryService = categoryService;
        this.marketIndexService = marketIndexService;
        this.priceRecommendationService = priceRecommendationService;
        this.marketAdminCommand = new MarketAdminCommand(
                plugin,
                listingStore,
                adminHistoryService,
                adminHistoryExportService,
                marketIndexService,
                priceRecommendationService,
                customItemRegistry,
                marketRecalculationService,
                customEnchantStore,
                customItemTypeExtractor,
                customItemMetadataLogService,
                customItemOverrideStore,
                customItemCollisionLogService
        );
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            if (args.length == 0) { handleMain(sender); return; }
            String root = args[0].toLowerCase(Locale.ROOT);
            if (marketAdminCommand.handlesRootToken(root)) { marketAdminCommand.execute(sender, args); return; }
            switch (root) {
                case "all" -> handleAll(sender);
                case "search" -> handleSearch(sender, args);
                case "claim" -> handleClaim(sender, args);
                case "history" -> handleHistory(sender, args);
                case "pricehistory" -> handlePriceHistory(sender, args);
                case "list" -> handleList(sender, args);
                default -> {
                    if (isKnownCategory(root)) handleCategory(sender, root);
                    else sender.sendRichMessage("<red>Unknown market command or category.</red> <gray>Try /market search &lt;query&gt;.</gray>");
                }
            }
        } catch (SecurityException exception) {
            sender.sendRichMessage("<red>You do not have permission to use that command.</red>");
        } catch (Exception exception) {
            sender.sendRichMessage("<red>Command failed:</red> <gray>" + escapeMini(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()) + "</gray>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            Set<String> suggestions = new LinkedHashSet<>();
            suggestions.add("all"); suggestions.add("search"); suggestions.add("claim"); suggestions.add("list"); suggestions.add("history"); suggestions.add("pricehistory"); suggestions.addAll(loadCategoryIds(false));
            if (sender.hasPermission("divinemarketplace.admin")) suggestions.addAll(marketAdminCommand.suggest(sender, args.length == 0 ? new String[0] : args));
            return filterByPrefix(suggestions, currentToken(args));
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (marketAdminCommand.handlesRootToken(root)) return marketAdminCommand.suggest(sender, args);
        return switch (root) {
            case "search", "history", "pricehistory" -> args.length == 2 ? filterByPrefix(marketIndexService.getPlayerDisplayNames(), currentToken(args)) : List.of();
            case "claim" -> args.length == 2 ? filterByPrefix(List.of("earnings"), currentToken(args)) : List.of();
            default -> List.of();
        };
    }

    @Override
    public boolean canUse(CommandSender sender) { return sender.hasPermission("divinemarketplace.use") || sender.hasPermission("divinemarketplace.admin"); }

    private void handleMain(CommandSender sender) {
        List<CategorySummary> categories = categoryService.getTopLevelCategories(0, 12);
        sender.sendRichMessage("<gold>DivineMarketplace</gold>");
        if (categories.isEmpty()) { sender.sendRichMessage("<gray>No player-visible categories currently have browse data.</gray>"); return; }
        for (CategorySummary category : categories) sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(category.categoryId()) + "</white> <dark_gray>(" + category.activeListingCount() + " listings)</dark_gray>");
    }

    private void handleAll(CommandSender sender) {
        var listings = listingStore.findAll(divinejason.divinemarketplace.config.ConfigService.get().defaultSortMode(), 0, 10);
        sender.sendRichMessage("<gold>All Listings</gold>");
        if (listings.isEmpty()) { sender.sendRichMessage("<gray>No active listings.</gray>"); return; }
        for (var listing : listings) sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(listing.marketDisplayName()) + "</white> <gray>x" + listing.amount() + "</gray> <yellow>$" + MONEY_FORMAT.format(listing.unitPrice() / 100.0) + "</yellow>");
    }

    private void handleCategory(CommandSender sender, String categoryId) {
        List<SubcategorySummary> groups = categoryService.getMarketGroupsForCategory(categoryId, 0, divinejason.divinemarketplace.config.ConfigService.get().searchMaxResultsPerPage());
        sender.sendRichMessage("<gold>Category</gold> <gray>-</gray> <white>" + escapeMini(categoryId) + "</white>");
        if (groups.isEmpty()) { sender.sendRichMessage("<gray>No active market groups found in that category.</gray>"); return; }
        for (SubcategorySummary group : groups) sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(group.marketDisplayName()) + "</white> <dark_gray>" + escapeMini(group.marketKey()) + "</dark_gray> <gray>(" + group.activeListingCount() + " listed)</gray>");
    }

    private void handleSearch(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market search &lt;query&gt;</gray>"); return; }
        String query = joinArgs(args, 1);
        List<SubcategorySummary> groups = categoryService.searchMarketGroups(query, 0, divinejason.divinemarketplace.config.ConfigService.get().searchMaxResultsPerPage());
        sender.sendRichMessage("<gold>Search</gold> <gray>-</gray> <white>" + escapeMini(query) + "</white>");
        if (groups.isEmpty()) { sender.sendRichMessage("<gray>No matching market groups found.</gray>"); return; }
        for (SubcategorySummary group : groups) {
            long recommended = priceRecommendationService.getRecommendedUnitPrice(group.marketKey());
            sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(group.marketDisplayName()) + "</white> <dark_gray>" + escapeMini(group.marketKey()) + "</dark_gray> <gray>(" + group.activeListingCount() + " listed)</gray> <yellow>~$" + MONEY_FORMAT.format(recommended / 100.0) + "</yellow>");
        }
    }

    private void handleClaim(CommandSender sender, String[] args) {
        requirePlayer(sender);
        Player player = (Player) sender;
        if (args.length == 1) { sender.sendRichMessage("<yellow>Claims menu is not wired yet.</yellow> <gray>Use /market claim earnings for now.</gray>"); return; }
        if (args[1].equalsIgnoreCase("earnings")) { claimService.claimEarnings(player); sender.sendRichMessage("<green>Processed earnings claim.</green>"); return; }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market claim</gray> <yellow>or</yellow> <gray>/market claim earnings</gray>");
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market history &lt;marketdisplayname&gt;</gray>"); return; }
        String token = joinArgs(args, 1);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market matched that name.</red>"); return; }
        List<SaleRecord> sales = historyService.getSaleHistory(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Sale History</gold> <gray>-</gray> <white>" + escapeMini(token) + "</white>");
        if (sales.isEmpty()) { sender.sendRichMessage("<gray>No recent exact sale history found.</gray>"); return; }
        for (SaleRecord sale : sales) sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(sale.marketDisplayName()) + "</white> <gray>x" + sale.amountPurchased() + "</gray> <yellow>$" + MONEY_FORMAT.format(sale.unitPrice() / 100.0) + "</yellow>");
    }

    private void handlePriceHistory(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market pricehistory &lt;marketdisplayname&gt; [menu]</gray>"); return; }
        boolean menu = args[args.length - 1].equalsIgnoreCase("menu");
        String token = joinArgs(args, 1, menu ? args.length - 1 : args.length);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market matched that name.</red>"); return; }
        if (!historyService.isPriceHistoryEnabled(marketKey)) { sender.sendRichMessage("<yellow>Price history is disabled for that market.</yellow>"); return; }
        if (menu) { sender.sendRichMessage("<yellow>Price history GUI option recognized, but menu wiring is not finished yet.</yellow>"); return; }
        List<RecommendationHistoryPoint> points = historyService.getPriceHistory(marketKey, YearMonth.now());
        sender.sendRichMessage("<gold>Price History</gold> <gray>-</gray> <white>" + escapeMini(token) + "</white>");
        if (points.isEmpty()) { sender.sendRichMessage("<gray>No daily recommended-price history found for this month.</gray>"); return; }
        long min = Long.MAX_VALUE; long max = 0L; long total = 0L;
        for (RecommendationHistoryPoint point : points) {
            min = Math.min(min, point.recommendedUnitPrice()); max = Math.max(max, point.recommendedUnitPrice()); total += point.recommendedUnitPrice();
            String day = Instant.ofEpochMilli(point.recordedAtEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_FORMAT);
            sender.sendRichMessage("<gray>-</gray> <white>" + day + "</white> <yellow>$" + MONEY_FORMAT.format(point.recommendedUnitPrice() / 100.0) + "</yellow>");
        }
        double average = total / (double) points.size();
        sender.sendRichMessage("<dark_gray>Min:</dark_gray> <yellow>$" + MONEY_FORMAT.format(min / 100.0) + "</yellow> <dark_gray>Max:</dark_gray> <yellow>$" + MONEY_FORMAT.format(max / 100.0) + "</yellow> <dark_gray>Avg:</dark_gray> <yellow>$" + MONEY_FORMAT.format(average / 100.0) + "</yellow>");
    }

    private void handleList(CommandSender sender, String[] args) {
        requirePlayer(sender);
        if (args.length < 3) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market list &lt;quantity&gt; &lt;unitPrice&gt;</gray>"); return; }
        Player player = (Player) sender;
        int quantity; long unitPriceHundredths;
        try { quantity = Integer.parseInt(args[1]); unitPriceHundredths = parseMoneyToHundredths(args[2]); }
        catch (Exception exception) { sender.sendRichMessage("<red>Invalid quantity or unit price.</red>"); return; }
        ItemStack sourceItem = player.getInventory().getItemInMainHand();
        ListingCreateResult result = listingService.createOrMergeListing(player, sourceItem, quantity, unitPriceHundredths);
        if (!result.success()) {
            sender.sendRichMessage("<red>Listing failed:</red> <gray>" + escapeMini(result.failureReason().name()) + "</gray> <dark_gray>(" + escapeMini(result.debugMessage() == null ? "" : result.debugMessage()) + ")</dark_gray>");
            return;
        }
        String actionWord = result.mergedIntoExisting() ? "Merged listing" : "Created listing";
        sender.sendRichMessage("<green>" + actionWord + ".</green> <white>" + escapeMini(result.marketDisplayName()) + "</white> <gray>x" + result.actualQuantity() + "</gray> <yellow>@ $" + MONEY_FORMAT.format(unitPriceHundredths / 100.0) + "</yellow>");
    }

    private void requirePlayer(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalStateException("This command can only be used by a player."); }
    private boolean isKnownCategory(String token) { return loadCategoryIds(false).stream().anyMatch(category -> category.equalsIgnoreCase(token)); }
    private List<String> loadCategoryIds(boolean includeUnsorted) { return marketIndexService.getCategoryIds(includeUnsorted); }
    private Collection<String> filterByPrefix(Collection<String> input, String currentToken) { String normalized = currentToken.toLowerCase(Locale.ROOT); return input.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList(); }
    private String currentToken(String[] args) { return args.length == 0 ? "" : args[args.length - 1]; }
    private String joinArgs(String[] args, int startInclusive) { return joinArgs(args, startInclusive, args.length); }
    private String joinArgs(String[] args, int startInclusive, int endExclusive) { StringBuilder builder = new StringBuilder(); for (int i = startInclusive; i < endExclusive; i++) { if (i > startInclusive) builder.append(' '); builder.append(args[i]); } return builder.toString().trim(); }
    private long parseMoneyToHundredths(String raw) { BigDecimal parsed = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY); return parsed.movePointRight(2).longValueExact(); }
    private String escapeMini(String input) { if (input == null) return ""; return input.replace("<", "\\<").replace(">", "\\>"); }
}
