package divinejason.divinemarketplace.auction.service.identity;


/*
 * File role: Extracts config driven custom item type signals from ItemStacks for identity and custom-item resolution.
 */
import divinejason.divinemarketplace.auction.model.CustomItemOverrideRecord;
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.config.MainConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ConfigDrivenCustomItemTypeExtractor implements CustomItemTypeExtractor {
    private final SQLiteCustomItemOverrideStore overrideStore;

    public ConfigDrivenCustomItemTypeExtractor(SQLiteCustomItemOverrideStore overrideStore) {
        this.overrideStore = Objects.requireNonNull(overrideStore, "overrideStore");
    }

    @Override
    public CustomItemTypeExtractionResult inspect(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return new CustomItemTypeExtractionResult(false, false, false, null, null, null, null, null, null, List.of("item was null or air"));
        }

        SerializedItemSignalView view = SerializedItemSignalView.from(itemStack);
        List<String> trace = new ArrayList<>();

        for (MainConfig.Rule rule : ConfigService.get().customIdentityRules()) {
            String matchedValue = matchRule(view, rule);
            trace.add(rule.id() + ": " + (matchedValue == null ? "no match" : "match=" + matchedValue));
            if (matchedValue == null || matchedValue.isBlank()) continue;

            String itemType = buildItemType(rule, view, matchedValue);
            String signature = buildRuleSignature(rule.id(), view.material(), view.customModelData(), matchedValue);
            CustomItemOverrideRecord override = overrideStore.findBySignature(signature);
            if (override != null && override.treatAsVanilla()) {
                trace.add("override: TREAT_AS_VANILLA");
                return new CustomItemTypeExtractionResult(false, true, false, null, view.material(), view.customModelData(), rule.id(), matchedValue, signature, List.copyOf(trace));
            }
            return new CustomItemTypeExtractionResult(true, false, false, itemType, view.material(), view.customModelData(), rule.id(), matchedValue, signature, List.copyOf(trace));
        }

        if (ConfigService.get().unknownCustomModelDataEnabled() && view.customModelData() != null) {
            String signature = buildUnknownSignature(view.material(), view.customModelData());
            CustomItemOverrideRecord override = overrideStore.findBySignature(signature);
            if (override != null && override.treatAsVanilla()) {
                trace.add("unknown_cmd_fallback: overridden to vanilla");
                return new CustomItemTypeExtractionResult(false, true, true, null, view.material(), view.customModelData(), "unknown_custom_model_data", Float.toString(view.customModelData()), signature, List.copyOf(trace));
            }
            String itemType = "unknown:" + view.material().name().toLowerCase(Locale.ROOT) + ":" + sanitizeCmd(view.customModelData());
            trace.add("unknown_cmd_fallback: " + itemType);
            return new CustomItemTypeExtractionResult(true, false, true, itemType, view.material(), view.customModelData(), "unknown_custom_model_data", Float.toString(view.customModelData()), signature, List.copyOf(trace));
        }

        trace.add("no custom match; treat as vanilla");
        return new CustomItemTypeExtractionResult(false, false, false, null, view.material(), view.customModelData(), null, null, buildVanillaInspectionSignature(view.material(), view.customModelData()), List.copyOf(trace));
    }

    private String matchRule(SerializedItemSignalView view, MainConfig.Rule rule) {
        String source = normalize(rule.source());
        return switch (source) {
            case "PUBLIC_BUKKIT_VALUES" -> view.findSectionKey("PublicBukkitValues", rule.key());
            case "SERIALIZE_SECTION" -> view.findSectionKey(rule.section(), rule.key());
            case "ANY_KEY", "DIRECT_KEY", "" -> view.findAnyKey(rule.key());
            default -> view.findAnyKey(rule.key());
        };
    }

    private String buildItemType(MainConfig.Rule rule, SerializedItemSignalView view, String matchedValue) {
        String base = switch (normalize(rule.resultMode())) {
            case "PREFIXED_VALUE" -> {
                String prefix = rule.prefix() == null ? "" : rule.prefix().trim();
                yield prefix.isBlank() ? matchedValue : prefix + ":" + matchedValue;
            }
            case "RAW_VALUE", "" -> matchedValue;
            default -> matchedValue;
        };

        StringBuilder builder = new StringBuilder(base);
        if (rule.appendMaterial()) builder.append(":").append(view.material().name().toLowerCase(Locale.ROOT));
        if (rule.appendCustomModelData() && view.customModelData() != null) builder.append(":").append(sanitizeCmd(view.customModelData()));
        return builder.toString();
    }

    private String buildRuleSignature(String ruleId, Material material, Float customModelData, String matchedValue) {
        return "rule|" + ruleId + "|" + material.name() + "|" + (customModelData == null ? "nocmd" : sanitizeCmd(customModelData)) + "|" + matchedValue;
    }

    private String buildUnknownSignature(Material material, Float customModelData) {
        return "unknown_cmd|" + material.name() + "|" + sanitizeCmd(customModelData);
    }

    private String buildVanillaInspectionSignature(Material material, Float customModelData) {
        return "vanilla_like|" + material.name() + "|" + (customModelData == null ? "nocmd" : sanitizeCmd(customModelData));
    }

    private String sanitizeCmd(Float value) {
        return Float.toString(value).replace('.', '_');
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
