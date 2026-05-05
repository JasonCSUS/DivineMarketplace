package divinejason.divinemarketplace.auction.service;


/*
 * File role: Contains marketplace service logic for serialized item signal view.
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class SerializedItemSignalView {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ItemStack itemStack;
    private final Map<String, Object> serialized;
    private final Material material;
    private final Float customModelData;
    private final String displayName;
    private final List<String> persistentDataKeys;

    private SerializedItemSignalView(ItemStack itemStack, Map<String, Object> serialized, Material material, Float customModelData, String displayName, List<String> persistentDataKeys) {
        this.itemStack = itemStack;
        this.serialized = serialized;
        this.material = material;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.persistentDataKeys = List.copyOf(persistentDataKeys == null ? List.of() : persistentDataKeys);
    }

    public static SerializedItemSignalView from(ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack");
        return new SerializedItemSignalView(
                itemStack,
                new LinkedHashMap<>(itemStack.serialize()),
                itemStack.getType(),
                readCustomModelData(itemStack.getItemMeta()),
                readDisplayName(itemStack),
                readPersistentDataKeys(itemStack.getItemMeta())
        );
    }

    public Material material() { return material; }
    public Float customModelData() { return customModelData; }
    public String displayName() { return displayName; }
    public List<String> persistentDataKeys() { return persistentDataKeys; }
    public Map<String, Object> serialized() { return Collections.unmodifiableMap(serialized); }

    public List<String> publicBukkitValueKeys() {
        Object section = findFirstSectionRecursive(serialized, "PublicBukkitValues");
        if (!(section instanceof Map<?, ?> map)) {
            return List.of();
        }
        return map.keySet().stream()
                .map(String::valueOf)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> serializedSectionPaths() {
        List<String> out = new ArrayList<>();
        collectSectionPaths(serialized, "", out);
        return out.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public String findAnyKey(String exactKey) {
        Object value = findFirstValueRecursive(serialized, exactKey);
        return value == null ? null : String.valueOf(value);
    }

    public String findSectionKey(String sectionName, String exactKey) {
        Object section = findFirstSectionRecursive(serialized, sectionName);
        if (!(section instanceof Map<?, ?> sectionMap)) {
            return null;
        }
        Object value = findFirstValueRecursive(sectionMap, exactKey);
        return value == null ? null : String.valueOf(value);
    }

    public List<String> toSummaryLines(CustomItemTypeExtractionResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("material: " + material.name());
        lines.add("customModelData: " + (customModelData == null ? "(none)" : customModelData));
        lines.add("displayName: " + (displayName == null ? "(none)" : displayName));
        if (result != null) {
            lines.add("custom: " + result.custom());
            lines.add("treatAsVanilla: " + result.treatAsVanilla());
            lines.add("provisional: " + result.provisional());
            lines.add("itemType: " + (result.itemType() == null ? "(none)" : result.itemType()));
            lines.add("matchedRule: " + (result.matchedRuleId() == null ? "(none)" : result.matchedRuleId()));
            lines.add("matchedValue: " + (result.matchedValue() == null ? "(none)" : result.matchedValue()));
            lines.add("signature: " + (result.signature() == null ? "(none)" : result.signature()));
        }
        return lines;
    }

    public List<String> toDetailedLines(CustomItemTypeExtractionResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("DivineMarketplace item inspection snapshot");
        lines.add("=======================================");
        lines.addAll(toSummaryLines(result));
        lines.add("");
        lines.add("ruleTrace:");
        if (result != null && result.ruleTrace() != null && !result.ruleTrace().isEmpty()) {
            for (String line : result.ruleTrace()) {
                lines.add(" - " + line);
            }
        } else {
            lines.add(" - (none)");
        }
        lines.add("");
        lines.add("serialized:");
        lines.add(prettySerialize(serialized, 0));
        return lines;
    }

    private static Object findFirstValueRecursive(Object node, String exactKey) {
        if (node instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (exactKey.equals(key)) {
                    return value;
                }
                Object nested = findFirstValueRecursive(value, exactKey);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                Object nested = findFirstValueRecursive(child, exactKey);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static Object findFirstSectionRecursive(Object node, String sectionName) {
        if (node instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (sectionName.equals(key)) {
                    return value;
                }
                Object nested = findFirstSectionRecursive(value, sectionName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                Object nested = findFirstSectionRecursive(child, sectionName);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static List<String> readPersistentDataKeys(ItemMeta itemMeta) {
        if (itemMeta == null) {
            return List.of();
        }
        return itemMeta.getPersistentDataContainer().getKeys().stream()
                .map(key -> key.getNamespace() + ":" + key.getKey())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static void collectSectionPaths(Object node, String path, List<String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isBlank() ? key : path + "." + key;
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                    out.add(childPath);
                    collectSectionPaths(value, childPath, out);
                }
            }
            return;
        }
        if (node instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object child : iterable) {
                collectSectionPaths(child, path + "[" + index + "]", out);
                index++;
            }
        }
    }

    private static Float readCustomModelData(ItemMeta itemMeta) {
        if (itemMeta == null || !itemMeta.hasCustomModelDataComponent()) return null;
        CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
        List<Float> floats = component.getFloats();
        if (floats == null || floats.isEmpty()) return null;
        return floats.get(0);
    }

    private static String readDisplayName(ItemStack itemStack) {
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return humanizeToken(itemStack.getType().name());
        String name = componentToPlain(meta.customName()); if (name != null) return name;
        name = componentToPlain(meta.displayName()); if (name != null) return name;
        name = componentToPlain(meta.itemName()); if (name != null) return name;
        return humanizeToken(itemStack.getType().name());
    }

    private static String componentToPlain(Component component) {
        if (component == null) return null;
        String plain = PLAIN.serialize(component).trim();
        return plain.isBlank() ? null : plain;
    }

    private static String humanizeToken(String token) {
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1));
        }
        return builder.toString();
    }

    private static String prettySerialize(Object value, int depth) {
        String indent = "  ".repeat(Math.max(0, depth));
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder();
            out.append("{\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.append(indent).append("  ").append(String.valueOf(entry.getKey())).append(": ")
                        .append(prettySerialize(entry.getValue(), depth + 1)).append("\n");
            }
            out.append(indent).append("}");
            return out.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder out = new StringBuilder();
            out.append("[\n");
            for (Object child : iterable) {
                out.append(indent).append("  ").append(prettySerialize(child, depth + 1)).append("\n");
            }
            out.append(indent).append("]");
            return out.toString();
        }
        return String.valueOf(value);
    }
}
