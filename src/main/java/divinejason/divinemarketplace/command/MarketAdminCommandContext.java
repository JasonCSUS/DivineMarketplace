package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.service.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.service.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.PriceRecommendationService;
import divinejason.divinemarketplace.auction.service.StoredEnchantExtractor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class MarketAdminCommandContext {
    static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    final DivineMarketplace plugin;
    final SQLiteListingStore listingStore;
    final DefaultAdminHistoryService adminHistoryService;
    final AdminHistoryExportService adminHistoryExportService;
    final FlattenedMarketIndexService marketIndexService;
    final PriceRecommendationService priceRecommendationService;
    final CustomItemRegistry customItemRegistry;
    final MarketRecalculationService marketRecalculationService;
    final SQLiteCustomEnchantStore customEnchantStore;
    final CustomItemTypeExtractor customItemTypeExtractor;
    final CustomItemMetadataLogService metadataLogService;
    final SQLiteCustomItemOverrideStore overrideStore;
    final CustomItemCollisionLogService collisionLogService;
    final StoredEnchantExtractor storedEnchantExtractor;

    MarketAdminCommandContext(
            DivineMarketplace plugin,
            SQLiteListingStore listingStore,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            CustomItemRegistry customItemRegistry,
            MarketRecalculationService marketRecalculationService,
            SQLiteCustomEnchantStore customEnchantStore,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService metadataLogService,
            SQLiteCustomItemOverrideStore overrideStore,
            CustomItemCollisionLogService collisionLogService,
            StoredEnchantExtractor storedEnchantExtractor
    ) {
        this.plugin = plugin;
        this.listingStore = listingStore;
        this.adminHistoryService = adminHistoryService;
        this.adminHistoryExportService = adminHistoryExportService;
        this.marketIndexService = marketIndexService;
        this.priceRecommendationService = priceRecommendationService;
        this.customItemRegistry = customItemRegistry;
        this.marketRecalculationService = marketRecalculationService;
        this.customEnchantStore = customEnchantStore;
        this.customItemTypeExtractor = customItemTypeExtractor;
        this.metadataLogService = metadataLogService;
        this.overrideStore = overrideStore;
        this.collisionLogService = collisionLogService;
        this.storedEnchantExtractor = storedEnchantExtractor;
    }

    boolean hasAdminPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender.hasPermission("divinemarketplace.admin");
    }

    void require(CommandSender sender, String permission) {
        if (!hasAdminPermission(sender, permission)) {
            throw new SecurityException("No permission: " + permission);
        }
    }

    Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalStateException("This command can only be used by a player.");
        }
        return player;
    }

    Collection<String> filterByPrefix(Collection<String> input, String currentToken) {
        String normalized = currentToken.toLowerCase(Locale.ROOT);
        return input.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    String currentToken(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }

    String joinArgs(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    long parseMoneyToHundredths(String raw) {
        return new BigDecimal(raw)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }

    String sanitizeFileToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    String shorten(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        if (maxLength <= 0 || input.length() <= maxLength) {
            return input;
        }
        if (maxLength <= 3) {
            return input.substring(0, maxLength);
        }
        return input.substring(0, maxLength - 3) + "...";
    }

    String escapeMini(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    String formatAdminRecord(AdminTransactionRecord record) {
        return "<gray>-</gray> <white>" + escapeMini(record.marketDisplayName() == null ? "(no item)" : record.marketDisplayName())
                + "</white> <gray>x" + record.amount()
                + "</gray> <yellow>$" + MONEY_FORMAT.format(record.totalPrice() / 100.0)
                + "</yellow> <dark_gray>[" + escapeMini(record.status() == null ? "?" : record.status()) + "]</dark_gray>";
    }

    Path writeRecordExport(String stem, String header, List<AdminTransactionRecord> records) {
        try {
            Path exportDirectory = plugin.getDataFolder().toPath().resolve("logs").resolve("exports");
            Files.createDirectories(exportDirectory);
            Path output = exportDirectory.resolve(stem + "_" + FILE_TIME.format(Instant.now()) + ".txt");
            List<String> lines = new ArrayList<>();
            lines.add(header);
            lines.add("Generated at: " + FILE_TIME.format(Instant.now()));
            lines.add("Record count: " + records.size());
            lines.add("");
            for (AdminTransactionRecord record : records) {
                lines.add(formatAdminRecord(record));
            }
            Files.write(output, lines);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write export file.", exception);
        }
    }
}
