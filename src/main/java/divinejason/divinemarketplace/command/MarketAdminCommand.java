package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.CustomItemOverrideRecord;
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
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
import divinejason.divinemarketplace.auction.service.SerializedItemSignalView;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class MarketAdminCommand {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    private final DivineMarketplace plugin;
    private final SQLiteListingStore listingStore;
    private final DefaultAdminHistoryService adminHistoryService;
    private final AdminHistoryExportService adminHistoryExportService;
    private final FlattenedMarketIndexService marketIndexService;
    private final PriceRecommendationService priceRecommendationService;
    private final CustomItemRegistry customItemRegistry;
    private final MarketRecalculationService marketRecalculationService;
    private final SQLiteCustomEnchantStore customEnchantStore;
    private final CustomItemTypeExtractor customItemTypeExtractor;
    private final CustomItemMetadataLogService metadataLogService;
    private final SQLiteCustomItemOverrideStore overrideStore;
    private final CustomItemCollisionLogService collisionLogService;

    public MarketAdminCommand(
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
            CustomItemCollisionLogService collisionLogService
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
                || normalized.equals("admin")
                || normalized.equals("inspect")
                || normalized.equals("custom");
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
            case "inspect" -> handleInspect(sender, args);
            case "custom" -> handleCustom(sender, args);
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
            case "define" -> List.of();
            case "enchant" -> suggestEnchant(args);
            case "admin" -> suggestAdmin(args);
            case "inspect" -> suggestInspect(args);
            case "custom" -> suggestCustom(args);
            default -> List.of();
        };
    }

    private void handleReload(CommandSender sender) { require(sender, "divinemarketplace.admin.reload"); plugin.reloadRuntimeData(); sender.sendRichMessage("<green>DivineMarketplace runtime data reloaded.</green>"); }

    private void handleRecalc(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.recalc");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market recalc all</gray> <yellow>or</yellow> <gray>/market recalc &lt;marketKey/displayName&gt;</gray>");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            marketRecalculationService.scheduleGlobalRecalculation();
            sender.sendRichMessage("<green>Scheduled global async market recalculation.</green>");
            return;
        }
        String token = joinArgs(args, 1, args.length);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market matched that token.</red>"); return; }
        marketRecalculationService.scheduleMarketRecalculation(marketKey);
        sender.sendRichMessage("<green>Scheduled async recalculation for:</green> <white>" + escapeMini(marketKey) + "</white>");
    }

    private void handleSetPrice(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.price.set");
        if (args.length < 3) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market setprice &lt;marketKey/displayName&gt; &lt;price&gt;</gray>");
            return;
        }
        long price;
        try { price = parseMoneyToHundredths(args[args.length - 1]); }
        catch (Exception exception) { sender.sendRichMessage("<red>Invalid price.</red>"); return; }
        String token = joinArgs(args, 1, args.length - 1);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market matched that token.</red>"); return; }
        priceRecommendationService.setManualRecommendedUnitPrice(marketKey, price);
        sender.sendRichMessage("<green>Set recommended price for</green> <white>" + escapeMini(marketKey) + "</white> <green>to</green> <yellow>$" + MONEY_FORMAT.format(price / 100.0) + "</yellow>");
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
        List<AdminTransactionRecord> records = switch (historyType) {
            case "buy" -> adminHistoryService.getSaleHistoryByPlayer(targetUuid, 0, 50).stream().filter(record -> targetUuid.equals(record.buyerUuid())).limit(50).toList();
            case "sell" -> adminHistoryService.getListingHistoryByPlayer(targetUuid, 0, 50).stream().limit(50).toList();
            case "claim" -> adminHistoryService.getClaimHistoryByPlayer(targetUuid, 0, 50).stream().limit(50).toList();
            default -> null;
        };
        if (records == null) { sender.sendRichMessage("<red>History type must be buy, sell, or claim.</red>"); return; }
        if (export) {
            require(sender, "divinemarketplace.admin.history.export");
            Path exportPath = writeRecordExport("playerhistory_" + sanitizeFileToken(args[1]) + "_" + historyType, "Player history for " + (target.getName() != null ? target.getName() : args[1]) + " (" + historyType + ")", records);
            sender.sendRichMessage("<green>Export written:</green> <gray>" + escapeMini(exportPath.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        sender.sendRichMessage("<gold>Player History</gold> <gray>-</gray> <white>" + escapeMini(target.getName() != null ? target.getName() : args[1]) + "</white> <gray>(" + historyType + ")</gray>");
        if (records.isEmpty()) { sender.sendRichMessage("<gray>No matching records found.</gray>"); return; }
        for (AdminTransactionRecord record : records) sender.sendRichMessage(formatAdminRecord(record));
    }

    private void handleAuditHistory(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.history.view");
        if (args.length < 2) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market audithistory &lt;item-or-marketKey&gt; [export]</gray>");
            return;
        }
        boolean export = args[args.length - 1].equalsIgnoreCase("export");
        String token = joinArgs(args, 1, export ? args.length - 1 : args.length);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market key matched that token.</red>"); return; }
        if (export) {
            require(sender, "divinemarketplace.admin.history.export");
            Path exportPath = adminHistoryExportService.exportSalesByMarketKey(marketKey);
            sender.sendRichMessage("<green>Export written:</green> <gray>" + escapeMini(exportPath.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        List<AdminTransactionRecord> records = adminHistoryService.getSaleHistoryByMarketKey(marketKey, 0, 10);
        sender.sendRichMessage("<gold>Audit History</gold> <gray>-</gray> <white>" + escapeMini(marketKey) + "</white>");
        if (records.isEmpty()) { sender.sendRichMessage("<gray>No audit sale records found for that market key.</gray>"); return; }
        for (AdminTransactionRecord record : records) sender.sendRichMessage(formatAdminRecord(record));
    }

    private void handleSort(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.item.sort");
        if (args.length < 3) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market sort &lt;item-or-marketKey&gt; &lt;category&gt;</gray>"); return; }
        String categoryId = args[args.length - 1];
        if (!marketIndexService.getCategoryIds(true).contains(categoryId)) { sender.sendRichMessage("<red>Unknown category:</red> <gray>" + escapeMini(categoryId) + "</gray>"); return; }
        String token = joinArgs(args, 1, args.length - 1);
        String marketKey = marketIndexService.resolveMarketKeyToken(token);
        if (marketKey == null) { sender.sendRichMessage("<red>No market matched that token.</red>"); return; }
        if (!marketIndexService.updateCategory(marketKey, categoryId)) { sender.sendRichMessage("<red>That market entry could not be recategorized.</red>"); return; }
        plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>Moved</green> <white>" + escapeMini(marketKey) + "</white> <green>to category</green> <white>" + escapeMini(categoryId) + "</white>");
    }

    private void handleDefine(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.item.define");
        requirePlayer(sender);
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult inspection = customItemTypeExtractor.inspect(held);
        if (inspection == null || !inspection.hasCustomSignal()) { sender.sendRichMessage("<red>The held item did not resolve to a definable custom signal.</red>"); return; }
        if (args.length < 2) { sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market define &lt;marketDisplayName&gt;</gray>"); return; }
        String newDisplayName = joinArgs(args, 1, args.length);
        String categoryId = marketIndexService.getByMarketKey(inspection.itemType()) != null ? marketIndexService.getByMarketKey(inspection.itemType()).categoryId() : "unsorted";
        CustomItemDefinition updated = new CustomItemDefinition(inspection.itemType(), inspection.requiredMaterial(), inspection.requiredCustomModelData(), newDisplayName, categoryId, CustomItemDefinitionState.CONFIRMED);
        customItemRegistry.upsertDefinition(updated);
        plugin.reloadRuntimeData();
        sender.sendRichMessage("<green>Defined custom item</green> <white>" + escapeMini(updated.itemType()) + "</white> <green>as</green> <white>" + escapeMini(updated.marketDisplayName()) + "</white>");
    }

    private void handleEnchant(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.enchant.define");
        if (args.length < 5 || !args[1].equalsIgnoreCase("define")) {
            sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market enchant define &lt;plugin:enchant&gt; &lt;marketDisplayName&gt; &lt;item1,item2,item3,...&gt;</gray>");
            return;
        }
        String namespacedEnchantKey = args[2];
        String displayName = joinArgs(args, 3, args.length - 1);
        List<String> itemTokens = List.of(args[args.length - 1].split(","));
        if (!namespacedEnchantKey.contains(":")) { sender.sendRichMessage("<red>Enchant key must be namespaced like plugin:enchant.</red>"); return; }
        customEnchantStore.upsert(namespacedEnchantKey, displayName, itemTokens.stream().map(String::trim).filter(token -> !token.isBlank()).toList());
        sender.sendRichMessage("<green>Stored custom enchant definition for</green> <white>" + escapeMini(namespacedEnchantKey) + "</white>");
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("flagged")) {
            require(sender, "divinemarketplace.admin.flagged.view");
            List<FlattenedMarketIndexEntry> flagged = marketIndexService.getFlaggedEntries();
            sender.sendRichMessage("<gold>Flagged / Unsorted Entries</gold>");
            if (flagged.isEmpty()) { sender.sendRichMessage("<gray>No unsorted market entries found.</gray>"); return; }
            flagged.stream().limit(20).forEach(entry -> sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(entry.marketDisplayName()) + "</white> <dark_gray>" + escapeMini(entry.marketKey()) + "</dark_gray> <gray>(" + escapeMini(entry.categoryId()) + ")</gray> <dark_gray>" + escapeMini(entry.definitionState().name()) + "</dark_gray>"));
            if (flagged.size() > 20) sender.sendRichMessage("<dark_gray>Showing 20 of " + flagged.size() + " flagged entries.</dark_gray>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("collisions")) {
            handleAdminCollisions(sender, args);
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market admin flagged</gray> <yellow>or</yellow> <gray>/market admin collisions [page]</gray>");
    }

    private void handleInspect(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.inspect");
        requirePlayer(sender);
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult result = customItemTypeExtractor.inspect(held);
        SerializedItemSignalView view = SerializedItemSignalView.from(held);
        if (args.length >= 2 && args[1].equalsIgnoreCase("raw")) {
            if (!divinejason.divinemarketplace.config.ConfigService.get().writeInspectRawSnapshots()) {
                sender.sendRichMessage("<yellow>Raw inspect snapshots are disabled in config.</yellow>");
                return;
            }
            Path output = metadataLogService.writeSnapshot(held, result, "inspect_raw");
            sender.sendRichMessage("<green>Wrote raw metadata snapshot:</green> <gray>" + escapeMini(output.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("rules")) {
            sender.sendRichMessage("<gold>Inspect Rules</gold>");
            for (String line : result.ruleTrace()) sender.sendRichMessage("<gray>-</gray> " + escapeMini(line));
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("copyrule")) {
            sender.sendRichMessage("<gold>Rule Hint</gold>");
            for (String line : buildSuggestedRuleHints(result)) {
                sender.sendRichMessage("<gray>" + escapeMini(line) + "</gray>");
            }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("signals")) {
            sender.sendRichMessage("<gold>Item Signals</gold>");
            for (String line : buildSignalLines(view)) {
                sender.sendRichMessage("<gray>" + escapeMini(line) + "</gray>");
            }
            return;
        }
        CustomItemOverrideRecord override = result.signature() == null ? null : overrideStore.findBySignature(result.signature());
        CustomItemDefinition definition = findInspectedDefinition(result, view);
        sender.sendRichMessage("<gold>Item Inspect</gold>");
        sender.sendRichMessage("<gray>Status:</gray> <white>" + (result.hasCustomSignal() ? (result.provisional() ? "provisional custom" : "custom") : (result.treatAsVanilla() ? "forced vanilla" : "vanilla")) + "</white>");
        sender.sendRichMessage("<gray>ItemType:</gray> <white>" + escapeMini(result.itemType() == null ? "(none)" : result.itemType()) + "</white>");
        sender.sendRichMessage("<gray>Material:</gray> <white>" + escapeMini(view.material().name()) + "</white>");
        sender.sendRichMessage("<gray>CMD:</gray> <white>" + escapeMini(view.customModelData() == null ? "(none)" : Float.toString(view.customModelData())) + "</white>");
        sender.sendRichMessage("<gray>Matched rule:</gray> <white>" + escapeMini(result.matchedRuleId() == null ? "(none)" : result.matchedRuleId()) + "</white>");
        sender.sendRichMessage("<gray>Definition state:</gray> <white>" + escapeMini(definition == null ? "(none)" : definition.state().name()) + "</white>");
        sender.sendRichMessage("<gray>Signature:</gray> <white>" + escapeMini(result.signature() == null ? "(none)" : result.signature()) + "</white>");
        sender.sendRichMessage("<gray>Override:</gray> <white>" + (override == null ? "no" : "yes") + "</white> <dark_gray>mode=" + escapeMini(override == null ? "(none)" : override.mode()) + " note=" + escapeMini(override == null || override.note() == null || override.note().isBlank() ? "(none)" : override.note()) + "</dark_gray>");
        sender.sendRichMessage("<dark_gray>Use /market inspect rules, /market inspect signals, /market inspect raw, /market inspect copyrule</dark_gray>");
    }

    private void handleCustom(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.custom.override");
        requirePlayer(sender);
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult result = customItemTypeExtractor.inspect(held);
        if (result == null || result.signature() == null || result.signature().isBlank()) { sender.sendRichMessage("<red>The held item did not produce a stable override signature.</red>"); return; }
        if (args.length >= 2 && args[1].equalsIgnoreCase("vanilla")) {
            String note = args.length >= 3 ? joinArgs(args, 2, args.length) : "";
            overrideStore.putTreatAsVanilla(result.signature(), note);
            sender.sendRichMessage("<green>Marked held item signature as treat-as-vanilla.</green>");
            return;
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("removeoverride") || args[1].equalsIgnoreCase("clear"))) {
            overrideStore.delete(result.signature());
            sender.sendRichMessage("<green>Removed custom override for held item signature.</green>");
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market custom vanilla [note]</gray> <yellow>or</yellow> <gray>/market custom removeoverride</gray>");
    }

    private CustomItemDefinition findInspectedDefinition(CustomItemTypeExtractionResult result, SerializedItemSignalView view) {
        if (result != null && result.itemType() != null && !result.itemType().isBlank()) {
            CustomItemDefinition byType = customItemRegistry.findByItemType(result.itemType());
            if (byType != null) {
                return byType;
            }
        }
        if (view != null && view.material() != null && view.customModelData() != null) {
            return customItemRegistry.findByMaterialAndCustomModelData(view.material(), view.customModelData());
        }
        return null;
    }

    private void handleAdminCollisions(CommandSender sender, String[] args) {
        require(sender, "divinemarketplace.admin.collisions.view");
        int page = 1;
        if (args.length >= 3) {
            try { page = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException exception) { sender.sendRichMessage("<red>Invalid page number.</red>"); return; }
        }

        int pageSize = 8;
        int total = collisionLogService.countEntries();
        List<String> lines = collisionLogService.readPageNewestFirst(page - 1, pageSize);
        sender.sendRichMessage("<gold>Custom Item Collisions</gold> <gray>page " + page + "</gray>");
        if (lines.isEmpty()) {
            sender.sendRichMessage("<gray>No custom item collisions logged.</gray>");
            sender.sendRichMessage("<dark_gray>Log file:</dark_gray> <gray>" + escapeMini(collisionLogService.logFile().toAbsolutePath().toString()) + "</gray>");
            return;
        }
        for (String line : lines) {
            sender.sendRichMessage("<gray>-</gray> <white>" + escapeMini(shorten(line, 240)) + "</white>");
        }
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        sender.sendRichMessage("<dark_gray>Showing " + lines.size() + " of " + total + ". Page " + page + "/" + pages + ". Log: " + escapeMini(collisionLogService.logFile().toAbsolutePath().toString()) + "</dark_gray>");
    }

    private List<String> buildSignalLines(SerializedItemSignalView view) {
        List<String> lines = new ArrayList<>();
        lines.add("material=" + view.material().name());
        lines.add("customModelData=" + (view.customModelData() == null ? "(none)" : view.customModelData()));
        lines.add("pdcKeys=" + joinCompact(view.persistentDataKeys()));
        lines.add("publicBukkitValueKeys=" + joinCompact(view.publicBukkitValueKeys()));
        lines.add("serializedSections=" + joinCompact(view.serializedSectionPaths()));
        return lines;
    }

    private String joinCompact(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "(none)";
        }
        int limit = Math.min(values.size(), 12);
        String joined = String.join(", ", values.subList(0, limit));
        if (values.size() > limit) {
            joined += ", ... +" + (values.size() - limit) + " more";
        }
        return joined;
    }

private List<String> buildSuggestedRuleHints(CustomItemTypeExtractionResult result) {
    List<String> hints = new ArrayList<>();
    if (result == null) {
        hints.add("No inspection result available.");
        return hints;
    }

    if ("unknown_custom_model_data".equals(result.matchedRuleId())) {
        hints.add("No exact key matched.");
        hints.add("Hint: use /market inspect raw, then add an exact rule with source/key/section.");
        hints.add("Unknown CMD fallback is currently being used.");
        return hints;
    }

    if (result.matchedRuleId() == null) {
        hints.add("No configured rule matched this item.");
        hints.add("Hint: inspect raw metadata, then add a rule using one of:");
        hints.add("source=ANY_KEY, PUBLIC_BUKKIT_VALUES, or SERIALIZE_SECTION");
        return hints;
    }

    for (divinejason.divinemarketplace.config.MainConfig.Rule rule : ConfigService.get().customIdentityRules()) {
        if (!rule.id().equals(result.matchedRuleId())) {
            continue;
        }
        hints.add("Matched existing rule: " + rule.id());
        hints.add("source=" + rule.source() + " key=" + rule.key() + " section=" + (rule.section() == null || rule.section().isBlank() ? "(none)" : rule.section()));
        hints.add("matchedValue=" + (result.matchedValue() == null ? "(none)" : result.matchedValue()));
        hints.add("resultMode=" + rule.resultMode() + " prefix=" + (rule.prefix() == null ? "" : rule.prefix()));
        hints.add("Hint: appendMaterial=" + rule.appendMaterial() + ", appendCustomModelData=" + rule.appendCustomModelData() + " for this rule.");
        return hints;
    }

    hints.add("Matched rule id: " + result.matchedRuleId());
    hints.add("Matched value: " + (result.matchedValue() == null ? "(none)" : result.matchedValue()));
    return hints;
}

private Path writeRecordExport(String stem, String header, List<AdminTransactionRecord> records) {
        try {
            Path exportDirectory = plugin.getDataFolder().toPath().resolve("logs").resolve("exports");
            Files.createDirectories(exportDirectory);
            Path output = exportDirectory.resolve(stem + "_" + FILE_TIME.format(Instant.now()) + ".txt");
            List<String> lines = new ArrayList<>();
            lines.add(header); lines.add("Generated at: " + FILE_TIME.format(Instant.now())); lines.add("Record count: " + records.size()); lines.add("");
            for (AdminTransactionRecord record : records) lines.add(formatAdminRecord(record));
            Files.write(output, lines);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write export file.", exception);
        }
    }

    private void require(CommandSender sender, String permission) { if (!sender.hasPermission(permission) && !sender.hasPermission("divinemarketplace.admin")) throw new SecurityException("No permission: " + permission); }
    private void requirePlayer(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalStateException("This command can only be used by a player."); }

    private Collection<String> rootSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (sender.hasPermission("divinemarketplace.admin.reload") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("reload");
        if (sender.hasPermission("divinemarketplace.admin.recalc") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("recalc");
        if (sender.hasPermission("divinemarketplace.admin.price.set") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("setprice");
        if (sender.hasPermission("divinemarketplace.admin.history.view") || sender.hasPermission("divinemarketplace.admin")) { suggestions.add("playerhistory"); suggestions.add("audithistory"); }
        if (sender.hasPermission("divinemarketplace.admin.item.sort") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("sort");
        if (sender.hasPermission("divinemarketplace.admin.item.define") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("define");
        if (sender.hasPermission("divinemarketplace.admin.enchant.define") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("enchant");
        if (sender.hasPermission("divinemarketplace.admin.flagged.view") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("admin");
        if (sender.hasPermission("divinemarketplace.admin.inspect") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("inspect");
        if (sender.hasPermission("divinemarketplace.admin.custom.override") || sender.hasPermission("divinemarketplace.admin")) suggestions.add("custom");
        return suggestions;
    }

    private Collection<String> suggestRecalc(String[] args) { if (args.length == 2) { Set<String> tokens = new LinkedHashSet<>(); tokens.add("all"); tokens.addAll(marketIndexService.getAdminTokens()); return filterByPrefix(tokens, currentToken(args)); } return List.of(); }
    private Collection<String> suggestSetPrice(String[] args) { return args.length == 2 ? filterByPrefix(marketIndexService.getAdminTokens(), currentToken(args)) : List.of(); }
    private Collection<String> suggestPlayerHistory(String[] args) { if (args.length == 2) return filterByPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), currentToken(args)); if (args.length == 3) return filterByPrefix(List.of("buy", "sell", "claim"), currentToken(args)); if (args.length == 4) return filterByPrefix(List.of("export"), currentToken(args)); return List.of(); }
    private Collection<String> suggestAuditHistory(String[] args) { if (args.length == 2) return filterByPrefix(marketIndexService.getAdminTokens(), currentToken(args)); if (args.length == 3) return filterByPrefix(List.of("export"), currentToken(args)); return List.of(); }
    private Collection<String> suggestSort(String[] args) { if (args.length == 2) return filterByPrefix(marketIndexService.getAdminTokens(), currentToken(args)); if (args.length == 3) return filterByPrefix(marketIndexService.getCategoryIds(true), currentToken(args)); return List.of(); }
    private Collection<String> suggestEnchant(String[] args) { return args.length == 2 ? filterByPrefix(List.of("define"), currentToken(args)) : List.of(); }
    private Collection<String> suggestAdmin(String[] args) { return args.length == 2 ? filterByPrefix(List.of("flagged", "collisions"), currentToken(args)) : List.of(); }
    private Collection<String> suggestInspect(String[] args) { return args.length == 2 ? filterByPrefix(List.of("rules", "signals", "raw", "copyrule"), currentToken(args)) : List.of(); }
    private Collection<String> suggestCustom(String[] args) { return args.length == 2 ? filterByPrefix(List.of("vanilla", "removeoverride"), currentToken(args)) : List.of(); }

    private String formatAdminRecord(AdminTransactionRecord record) { return "<gray>-</gray> <white>" + escapeMini(record.marketDisplayName() == null ? "(no item)" : record.marketDisplayName()) + "</white> <gray>x" + record.amount() + "</gray> <yellow>$" + MONEY_FORMAT.format(record.totalPrice() / 100.0) + "</yellow> <dark_gray>[" + escapeMini(record.status() == null ? "?" : record.status()) + "]</dark_gray>"; }
    private Collection<String> filterByPrefix(Collection<String> input, String currentToken) { String normalized = currentToken.toLowerCase(Locale.ROOT); return input.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList(); }
    private String currentToken(String[] args) { return args.length == 0 ? "" : args[args.length - 1]; }
    private String joinArgs(String[] args, int startInclusive, int endExclusive) { StringBuilder builder = new StringBuilder(); for (int i = startInclusive; i < endExclusive; i++) { if (i > startInclusive) builder.append(' '); builder.append(args[i]); } return builder.toString().trim(); }
    private long parseMoneyToHundredths(String raw) { return new java.math.BigDecimal(raw).setScale(2, java.math.RoundingMode.UNNECESSARY).movePointRight(2).longValueExact(); }
    private String sanitizeFileToken(String value) { if (value == null || value.isBlank()) return "unknown"; return value.replaceAll("[^a-zA-Z0-9._-]", "_"); }

    private String shorten(String input, int maxLength) {
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

    private String escapeMini(String input) { if (input == null) return ""; return input.replace("<", "\\<").replace(">", "\\>"); }
}
