package divinejason.divinemarketplace.menu;


/*
 * File role: Holds validated GUI titles, slots, slot lists, and icon overrides loaded from menu.yml.
 */
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/**
 * Runtime menu visuals/layout map loaded from menu.yml.
 *
 * This intentionally works like a tiny internal GUI theme registry: code asks for stable keys
 * while admins can edit materials, names, lore, custom model data, titles, and slot maps.
 */
public final class MenuVisualConfig {
    private final Map<String, String> titlesByView;
    private final Map<String, MenuIconSpec> itemsByKey;
    private final Map<String, Integer> slotsByKey;
    private final Map<String, List<Integer>> slotListsByKey;

    MenuVisualConfig(
            Map<String, String> titlesByView,
            Map<String, MenuIconSpec> itemsByKey,
            Map<String, Integer> slotsByKey,
            Map<String, List<Integer>> slotListsByKey
    ) {
        this.titlesByView = Map.copyOf(titlesByView);
        this.itemsByKey = Map.copyOf(itemsByKey);
        this.slotsByKey = Map.copyOf(slotsByKey);
        this.slotListsByKey = Map.copyOf(slotListsByKey);
    }

    public String title(MenuView view, String fallback) {
        if (view == null) {
            return fallback;
        }
        return titlesByView.getOrDefault(normalize(view.name()), fallback);
    }

    public MenuIconSpec item(String key, Material fallbackMaterial, String fallbackName) {
        return itemsByKey.getOrDefault(normalize(key), MenuIconSpec.of(fallbackMaterial, fallbackName, List.of(), null));
    }

    public int slot(String key, int fallback) {
        return slotsByKey.getOrDefault(normalize(key), fallback);
    }

    public int[] slotList(String key, int[] fallback) {
        List<Integer> configured = slotListsByKey.get(normalize(key));
        if (configured == null || configured.isEmpty()) {
            return fallback;
        }
        int[] out = new int[configured.size()];
        for (int i = 0; i < configured.size(); i++) {
            out[i] = configured.get(i);
        }
        return out;
    }

    static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, String> titlesByView = new LinkedHashMap<>();
        private final Map<String, MenuIconSpec> itemsByKey = new LinkedHashMap<>();
        private final Map<String, Integer> slotsByKey = new LinkedHashMap<>();
        private final Map<String, List<Integer>> slotListsByKey = new LinkedHashMap<>();

        Builder title(String key, String value) {
            titlesByView.put(normalize(key), value);
            return this;
        }

        Builder item(String key, MenuIconSpec item) {
            itemsByKey.put(normalize(key), item);
            return this;
        }

        Builder slot(String key, int slot) {
            slotsByKey.put(normalize(key), slot);
            return this;
        }

        Builder slotList(String key, List<Integer> slots) {
            slotListsByKey.put(normalize(key), List.copyOf(slots));
            return this;
        }

        MenuVisualConfig build() {
            return new MenuVisualConfig(titlesByView, itemsByKey, slotsByKey, slotListsByKey);
        }
    }
}
