package divinejason.divinemarketplace.command;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.persistence.BinaryListingStore;
import divinejason.divinemarketplace.auction.persistence.BinarySalesStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.ClaimService;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.ListingService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Paper BasicCommand implementation for /market.
 *
 * Current command strategy:
 * - use Paper's BasicCommand suggest/canUse path for clean permission-aware UX
 * - expose market display names to players for history/search-style commands
 * - expose both market keys and market display names to admins for maintenance commands
 * - wire real low-risk flows now (list, claim earnings, history text views)
 * - keep unfinished menu-heavy or admin-maintenance flows explicit instead of pretending they are done
 */
@NullMarked
public final class MarketCommand implements BasicCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");

    private final DivineMarketplace plugin;
    private final ListingService listingService;
    private final ClaimService claimService;
    private final BinaryListingStore listingStore;
    private final BinarySalesStore salesStore;
    private final MarketAdminCommand marketAdminCommand;

    public MarketCommand(
            DivineMarketplace plugin,
            ListingService listingService,
            ClaimService claimService,
            BinaryListingStore listingStore,
            BinarySalesStore salesStore,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService
    ) {
        this.plugin = plugin;
        this.listingService = listingService;
        this.claimService = claimService;
        this.listingStore = listingStore;
        this.salesStore = salesStore;
        this.marketAdminCommand = new MarketAdminCommand(
                plugin,
                listingStore,
                adminHistoryService,
                adminHistoryExportService
        );
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        try {
            if (args.length == 0) {
                handleMain(sender);
                return;
            }

            String root = args[0].toLowerCase(Locale.ROOT);

            if (marketAdminCommand.handlesRootToken(root)) {
                marketAdminCommand.execute(sender, args);
                return;
            }

            switch (root) {
                case "all" -> handleAll(sender);
                case "search" -> handleSearch(sender, args);
                case "claim" -> handleClaim(sender, args);
                case "history" -> handleHistory(sender, args);
                case "pricehistory" -> handlePriceHistory(sender, args);
                case "list" -> handleList(sender, args);
                default -> {
                    if (isKnownCategory(root)) {
                        handleCategory(sender, root);
                        return;
                    }
                    sender.sendRichMessage("<red>Unknown market command or category.</red> <gray>Try /market search &lt;query&gt;.</gray>");
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
            suggestions.add("all");
            suggestions.add("search");
            suggestions.add("claim");
            suggestions.add("list");
            suggestions.add("history");
            suggestions.add("pricehistory");
            suggestions.addAll(loadCategoryIds(plugin));

            if (sender.hasPermission("divinemarketplace.admin")) {
                suggestions.addAll(marketAdminCommand.suggest(sender, args.length == 0 ? new String[0] : args));
            }

            return filterByPrefix(suggestions, currentToken(args));
        }

        String root = args[0].toLowerCase(Locale.ROOT);

        if (marketAdminCommand.handlesRootToken(root)) {
            return marketAdminCommand.suggest(sender, args);
        }

        return switch (root) {
            case "search" -> args.length == 2
                    ? filterByPrefix(collectPlayerMarketDisplayNames(), currentToken(args))
                    : List.of();
            case "claim" -> args.length == 2
                    ? filterByPrefix(List.of("earnings"), currentToken(args))
                    : List.of();
            case "history", "pricehistory" -> args.length == 2
                    ? filterByPrefix(collectPlayerMarketDisplayNames(), currentToken(args))
                    : List.of();
            default -> List.of();
        };
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("divinemarketplace.use") || sender.hasPermission("divinemarketplace.admin");
    }

    private void handleMain(CommandSender sender) {
        sender.sendRichMessage("<gold>DivineMarketplace</gold> <gray>-</gray> <yellow>Main menu command recognized.</yellow> <gray>GUI opening is not wired yet.</gray>");
    }

    private void handleAll(CommandSender sender) {
        sender.sendRichMessage("<gold>All Listings</gold> <gray>-</gray> <yellow>Command recognized.</yellow> <gray>Listing browser opening is not wired yet.</gray>");
    }

    private void handleCategory(CommandSender sender, String categoryId) {
        sender.sendRichMessage("<gold>Category</gold> <gray>-</gray> <white>" + escapeMini(categoryId) + "</white> <gray>recognized, but category browser opening is not wired yet.</gray>");
    }

    private void handleSearch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market search &lt;query&gt;</gray>");
            return;
        }

        String query = joinArgs(args, 1);
        sender.sendRichMessage("<gold>Search</gold> <gray>-</gray> <white>" + escapeMini(query) + "</white> <gray>recognized, but search-result routing is not wired yet.</gray>");
    }

    private void handleClaim(CommandSender sender, String[] args) {
        requirePlayer(sender);

        Player player = (Player) sender;
        if (args.length == 1) {
            sender.sendRichMessage("<gold>Claims</gold> <gray>-</gray> <yellow>Command recognized.</yellow> <gray>Claims menu opening is not wired yet.</gray>");
            return;
        }

        if (args[1].equalsIgnoreCase("earnings")) {
            claimService.claimEarnings(player);
            sender.sendRichMessage("<green>Processed earnings claim.</green>");
            return;
        }

        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market claim</gray> <yellow>or</yellow> <gray>/market claim earnings</gray>");
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market history &lt;marketdisplayname&gt;</gray>");
            return;
        }

        String token = joinArgs(args, 1);
        String marketKey = resolveMarketKeyFromToken(token);
        if (marketKey == null) {
            sender.sendRichMessage("<red>No active market matched that name.</red>");
            return;
        }

        var sales = salesStore.getSaleHistoryForMarketKey(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Sale History</gold> <gray>-</gray> <white>" + escapeMini(token) + "</white>");
        if (sales.isEmpty()) {
            sender.sendRichMessage("<gray>No recent exact sale history found.</gray>");
            return;
        }

        for (var sale : sales) {
            double unitPrice = sale.unitPrice() / 100.0;
            sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(sale.marketDisplayName()) + "</white> <gray>x" + sale.amountPurchased() + "</gray> <yellow>$" + MONEY_FORMAT.format(unitPrice) + "</yellow>");
        }
    }

    private void handlePriceHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market pricehistory &lt;marketdisplayname&gt;</gray>");
            return;
        }

        sender.sendRichMessage("<yellow>Price history command recognized, but price-history service wiring is not finished yet.</yellow>");
    }

    private void handleList(CommandSender sender, String[] args) {
        requirePlayer(sender);

        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market list &lt;quantity&gt; &lt;unitPrice&gt;</gray>");
            return;
        }

        Player player = (Player) sender;
        int quantity;
        long unitPriceHundredths;

        try {
            quantity = Integer.parseInt(args[1]);
            unitPriceHundredths = parseMoneyToHundredths(args[2]);
        } catch (Exception exception) {
            sender.sendRichMessage("<red>Invalid quantity or unit price.</red>");
            return;
        }

        ItemStack sourceItem = player.getInventory().getItemInMainHand();
        ListingCreateResult result = listingService.createOrMergeListing(player, sourceItem, quantity, unitPriceHundredths);

        if (!result.success()) {
            sender.sendRichMessage("<red>Listing failed:</red> <gray>" + escapeMini(result.failureReason().name()) + "</gray> <dark_gray>(" + escapeMini(result.debugMessage() == null ? "" : result.debugMessage()) + ")</dark_gray>");
            return;
        }

        String actionWord = result.mergedIntoExisting() ? "Merged listing" : "Created listing";
        sender.sendRichMessage(
                "<green>" + actionWord + ".</green> <white>" + escapeMini(result.marketDisplayName()) + "</white> "
                        + "<gray>x" + result.actualQuantity() + "</gray> <yellow>@ $" + MONEY_FORMAT.format(unitPriceHundredths / 100.0) + "</yellow>"
        );
    }

    private void requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            throw new IllegalStateException("This command can only be used by a player.");
        }
    }

    private String resolveMarketKeyFromToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);

        for (var listing : listingStore.getAllActive()) {
            if (listing.marketDisplayName().equalsIgnoreCase(token) || listing.marketKey().equalsIgnoreCase(token)) {
                return listing.marketKey();
            }
        }

        for (var listing : listingStore.getAllActive()) {
            if (listing.marketDisplayName().toLowerCase(Locale.ROOT).startsWith(normalized)
                    || listing.marketKey().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                return listing.marketKey();
            }
        }

        return null;
    }

    private boolean isKnownCategory(String token) {
        return loadCategoryIds(plugin).stream().anyMatch(category -> category.equalsIgnoreCase(token));
    }

    static List<String> loadCategoryIds(DivineMarketplace plugin) {
        var categoryConfigFile = divinejason.divinemarketplace.setup.PluginDirectoryLayout
                .resolveCategoryConfigFile(plugin.getDataFolder().toPath())
                .toFile();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(categoryConfigFile);
        ConfigurationSection categories = yaml.getConfigurationSection("categories");
        if (categories == null) {
            return List.of();
        }
        return new ArrayList<>(categories.getKeys(false));
    }

    private Set<String> collectPlayerMarketDisplayNames() {
        Set<String> displayNames = new LinkedHashSet<>();
        for (var listing : listingStore.getAllActive()) {
            displayNames.add(listing.marketDisplayName());
        }
        return displayNames;
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

    private String joinArgs(String[] args, int startInclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < args.length; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    private long parseMoneyToHundredths(String raw) {
        BigDecimal parsed = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
        return parsed.movePointRight(2).longValueExact();
    }

    private String escapeMini(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
