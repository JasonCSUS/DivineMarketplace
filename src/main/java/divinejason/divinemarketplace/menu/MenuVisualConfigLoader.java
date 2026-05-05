package divinejason.divinemarketplace.menu;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads editable menu visuals and slot maps from plugins/DivineMarketplace/menu.yml.
 *
 * Bad editable values are warned and ignored instead of being allowed to break
 * the market GUI. Code-side fallbacks in MenuSlots/MenuItemFactory remain the
 * source of truth when an admin-provided value is invalid.
 */
public final class MenuVisualConfigLoader {
    private static final int INVENTORY_SIZE = 54;

    private final Logger logger;
    private final Map<String, Map<Integer, String>> seenSlotsByRoot = new LinkedHashMap<>();

    public MenuVisualConfigLoader() {
        this(Logger.getLogger(MenuVisualConfigLoader.class.getName()));
    }

    public MenuVisualConfigLoader(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public MenuVisualConfig load(Path menuFile) {
        seenSlotsByRoot.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(menuFile.toFile());
        MenuVisualConfig.Builder builder = MenuVisualConfig.builder();
        loadTitles(yaml, builder);
        loadItems(yaml, builder);
        loadSlots(yaml, builder);
        return builder.build();
    }

    private void loadTitles(YamlConfiguration yaml, MenuVisualConfig.Builder builder) {
        ConfigurationSection section = yaml.getConfigurationSection("titles");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            builder.title(key, section.getString(key, key));
        }
    }

    private void loadItems(YamlConfiguration yaml, MenuVisualConfig.Builder builder) {
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) {
            return;
        }
        flattenItems(section, "", builder);
    }

    private void flattenItems(ConfigurationSection section, String prefix, MenuVisualConfig.Builder builder) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) {
                continue;
            }
            if (item.contains("material") || item.contains("name") || item.contains("customModelData")) {
                Material material = parseMaterial(path, item.getString("material"), Material.BARRIER);
                String name = item.getString("name", path);
                List<String> lore = item.getStringList("lore");
                Integer customModelData = null;
                if (item.contains("customModelData")) {
                    if (item.isInt("customModelData")) {
                        customModelData = item.getInt("customModelData");
                    } else {
                        warn("menu.yml items." + path + ".customModelData must be an integer. Ignoring custom model data for this item.");
                    }
                }
                builder.item(path, MenuIconSpec.of(material, name, lore, customModelData));
            } else {
                flattenItems(item, path, builder);
            }
        }
    }

    private void loadSlots(YamlConfiguration yaml, MenuVisualConfig.Builder builder) {
        ConfigurationSection section = yaml.getConfigurationSection("slots");
        if (section == null) {
            return;
        }
        flattenSlots(section, "", builder);
    }

    private void flattenSlots(ConfigurationSection section, String prefix, MenuVisualConfig.Builder builder) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object raw = section.get(key);
            if (raw instanceof Number number) {
                int slot = number.intValue();
                if (validSlot(path, slot) && registerSlot(path, slot)) {
                    builder.slot(path, slot);
                }
                continue;
            }
            if (raw instanceof List<?> list) {
                List<Integer> slots = new ArrayList<>();
                Set<Integer> localSlots = new LinkedHashSet<>();
                for (Object entry : list) {
                    if (!(entry instanceof Number number)) {
                        warn("menu.yml slots." + path + " contains a non-number entry. Ignoring it.");
                        continue;
                    }
                    int slot = number.intValue();
                    if (!validSlot(path, slot)) {
                        continue;
                    }
                    if (!localSlots.add(slot)) {
                        warn("menu.yml slots." + path + " repeats slot " + slot + ". Ignoring the duplicate entry.");
                        continue;
                    }
                    if (!registerSlot(path, slot)) {
                        continue;
                    }
                    slots.add(slot);
                }
                if (!slots.isEmpty()) {
                    builder.slotList(path, slots);
                } else {
                    warn("menu.yml slots." + path + " did not contain any usable slots. Code defaults will be used.");
                }
                continue;
            }
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                flattenSlots(child, path, builder);
            }
        }
    }

    private boolean validSlot(String path, int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            warn("menu.yml slots." + path + " uses invalid slot " + slot + ". Valid slots are 0-" + (INVENTORY_SIZE - 1) + ". Code defaults will be used for invalid entries.");
            return false;
        }
        return true;
    }

    private boolean registerSlot(String path, int slot) {
        String root = rootKey(path);
        Map<Integer, String> seen = seenSlotsByRoot.computeIfAbsent(root, ignored -> new LinkedHashMap<>());
        String existingPath = seen.putIfAbsent(slot, path);
        if (existingPath != null && !existingPath.equals(path)) {
            warn("menu.yml slots." + path + " reuses slot " + slot + " already assigned to slots." + existingPath + " in the same menu section. Ignoring the duplicate assignment.");
            return false;
        }
        return true;
    }

    private String rootKey(String path) {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }

    private Material parseMaterial(String path, String rawMaterial, Material fallback) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(rawMaterial.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            warn("menu.yml items." + path + ".material has unknown material '" + rawMaterial + "'. Falling back to " + fallback.name() + ".");
            return fallback;
        }
        return material;
    }

    private void warn(String message) {
        logger.warning(message);
    }
}
