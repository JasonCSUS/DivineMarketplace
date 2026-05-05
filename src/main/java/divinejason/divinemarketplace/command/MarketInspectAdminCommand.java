package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemOverrideRecord;
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import divinejason.divinemarketplace.auction.service.SerializedItemSignalView;
import divinejason.divinemarketplace.config.ConfigService;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MarketInspectAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketInspectAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        return root.equalsIgnoreCase("inspect");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        handleInspect(sender, args);
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        return args.length == 2
                ? context.filterByPrefix(List.of("rules", "signals", "raw", "copyrule"), context.currentToken(args))
                : List.of();
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        return context.hasAdminPermission(sender, "divinemarketplace.admin.inspect") ? List.of("inspect") : List.of();
    }

    private void handleInspect(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.inspect");
        Player player = context.requirePlayer(sender);
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemTypeExtractionResult result = context.customItemTypeExtractor.inspect(held);
        SerializedItemSignalView view = SerializedItemSignalView.from(held);
        if (args.length >= 2 && args[1].equalsIgnoreCase("raw")) {
            if (!ConfigService.get().writeInspectRawSnapshots()) {
                sender.sendRichMessage("<yellow>Raw inspect snapshots are disabled in config.</yellow>");
                return;
            }
            Path output = context.metadataLogService.writeSnapshot(held, result, "inspect_raw");
            sender.sendRichMessage("<green>Wrote raw metadata snapshot:</green> <gray>" + context.escapeMini(output.toAbsolutePath().toString()) + "</gray>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("rules")) {
            sender.sendRichMessage("<gold>Inspect Rules</gold>");
            for (String line : result.ruleTrace()) {
                sender.sendRichMessage("<gray>-</gray> " + context.escapeMini(line));
            }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("copyrule")) {
            sender.sendRichMessage("<gold>Rule Hint</gold>");
            for (String line : buildSuggestedRuleHints(result)) {
                sender.sendRichMessage("<gray>" + context.escapeMini(line) + "</gray>");
            }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("signals")) {
            sender.sendRichMessage("<gold>Item Signals</gold>");
            for (String line : buildSignalLines(held, view)) {
                sender.sendRichMessage("<gray>" + context.escapeMini(line) + "</gray>");
            }
            return;
        }
        CustomItemOverrideRecord override = result.signature() == null ? null : context.overrideStore.findBySignature(result.signature());
        CustomItemDefinition definition = findInspectedDefinition(result, view);
        sender.sendRichMessage("<gold>Item Inspect</gold>");
        sender.sendRichMessage("<gray>Status:</gray> <white>" + (result.hasCustomSignal() ? (result.provisional() ? "provisional custom" : "custom") : (result.treatAsVanilla() ? "forced vanilla" : "vanilla")) + "</white>");
        sender.sendRichMessage("<gray>ItemType:</gray> <white>" + context.escapeMini(result.itemType() == null ? "(none)" : result.itemType()) + "</white>");
        sender.sendRichMessage("<gray>Material:</gray> <white>" + context.escapeMini(view.material().name()) + "</white>");
        sender.sendRichMessage("<gray>CMD:</gray> <white>" + context.escapeMini(view.customModelData() == null ? "(none)" : Float.toString(view.customModelData())) + "</white>");
        sender.sendRichMessage("<gray>Matched rule:</gray> <white>" + context.escapeMini(result.matchedRuleId() == null ? "(none)" : result.matchedRuleId()) + "</white>");
        sender.sendRichMessage("<gray>Definition state:</gray> <white>" + context.escapeMini(definition == null ? "(none)" : definition.state().name()) + "</white>");
        sender.sendRichMessage("<gray>Signature:</gray> <white>" + context.escapeMini(result.signature() == null ? "(none)" : result.signature()) + "</white>");
        sender.sendRichMessage("<gray>Override:</gray> <white>" + (override == null ? "no" : "yes") + "</white> <dark_gray>mode="
                + context.escapeMini(override == null ? "(none)" : override.mode())
                + " note=" + context.escapeMini(override == null || override.note() == null || override.note().isBlank() ? "(none)" : override.note()) + "</dark_gray>");
        sender.sendRichMessage("<dark_gray>Use /market inspect rules, /market inspect signals, /market inspect raw, /market inspect copyrule</dark_gray>");
    }

    private CustomItemDefinition findInspectedDefinition(CustomItemTypeExtractionResult result, SerializedItemSignalView view) {
        if (result != null && result.itemType() != null && !result.itemType().isBlank()) {
            CustomItemDefinition byType = context.customItemRegistry.findByItemType(result.itemType());
            if (byType != null) {
                return byType;
            }
        }
        if (view != null && view.material() != null && view.customModelData() != null) {
            return context.customItemRegistry.findByMaterialAndCustomModelData(view.material(), view.customModelData());
        }
        return null;
    }

    private List<String> buildSignalLines(ItemStack held, SerializedItemSignalView view) {
        List<String> lines = new ArrayList<>();
        lines.add("material=" + view.material().name());
        lines.add("customModelData=" + (view.customModelData() == null ? "(none)" : view.customModelData()));
        lines.add("pdcKeys=" + joinCompact(view.persistentDataKeys()));
        lines.add("publicBukkitValueKeys=" + joinCompact(view.publicBukkitValueKeys()));
        lines.add("serializedSections=" + joinCompact(view.serializedSectionPaths()));
        lines.add("storedEnchants=" + joinCompact(storedEnchantSignals(held)));
        return lines;
    }

    private List<String> storedEnchantSignals(ItemStack held) {
        Map<Enchantment, Integer> storedEnchantments = context.storedEnchantExtractor.extractStoredEnchantments(held);
        if (storedEnchantments.isEmpty()) {
            return List.of();
        }
        return storedEnchantments.entrySet().stream()
                .map(entry -> context.storedEnchantExtractor.enchantKey(entry.getKey()) + "=" + entry.getValue())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
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
}
