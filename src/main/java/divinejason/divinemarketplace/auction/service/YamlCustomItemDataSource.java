package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * YAML-backed custom item definition storage.
 *
 * Notes:
 * - reads and writes the live plugin-folder custom_items.yml
 * - uses a sanitized storage key for section names so odd itemType values do not
 *   break YAML paths
 * - the real authoritative itemType still lives inside each definition entry
 */
public final class YamlCustomItemDataSource implements CustomItemDataSource {
    private final Path customItemsFile;

    public YamlCustomItemDataSource(JavaPlugin plugin) {
        this(PluginDirectoryLayout.resolveCustomItemsFile(plugin.getDataFolder().toPath()));
    }

    public YamlCustomItemDataSource(Path customItemsFile) {
        this.customItemsFile = Objects.requireNonNull(customItemsFile, "customItemsFile");
    }

    @Override
    public Map<String, CustomItemDefinition> loadDefinitions() {
        try {
            ensureFileExists();

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(customItemsFile.toFile());
            ConfigurationSection itemsSection = yaml.getConfigurationSection("items");

            Map<String, CustomItemDefinition> definitions = new LinkedHashMap<>();
            if (itemsSection == null) {
                return definitions;
            }

            for (String storageKey : itemsSection.getKeys(false)) {
                ConfigurationSection section = itemsSection.getConfigurationSection(storageKey);
                if (section == null) {
                    continue;
                }

                String itemType = section.getString("itemType");
                String materialName = section.getString("requiredMaterial");
                String marketDisplayName = section.getString("marketDisplayName");
                String categoryId = section.getString("categoryId", "unsorted");

                if (itemType == null || itemType.isBlank() || materialName == null || marketDisplayName == null || marketDisplayName.isBlank()) {
                    continue;
                }

                Material requiredMaterial = Material.matchMaterial(materialName);
                if (requiredMaterial == null) {
                    continue;
                }

                Float requiredCustomModelData = null;
                if (section.contains("requiredCustomModelData")) {
                    requiredCustomModelData = (float) section.getDouble("requiredCustomModelData");
                }

                CustomItemDefinition definition = new CustomItemDefinition(
                        itemType,
                        requiredMaterial,
                        requiredCustomModelData,
                        marketDisplayName,
                        categoryId
                );

                definitions.put(itemType, definition);
            }

            return definitions;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load custom item definitions from " + customItemsFile, exception);
        }
    }

    @Override
    public CustomItemDefinition upsertDefinition(CustomItemDefinition definition) {
        try {
            ensureFileExists();

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(customItemsFile.toFile());
            ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
            if (itemsSection == null) {
                itemsSection = yaml.createSection("items");
            }

            String storageKey = storageKeyFor(definition.itemType());
            ConfigurationSection section = itemsSection.getConfigurationSection(storageKey);
            if (section == null) {
                section = itemsSection.createSection(storageKey);
            }

            section.set("itemType", definition.itemType());
            section.set("requiredMaterial", definition.requiredMaterial().name());
            section.set("requiredCustomModelData", definition.requiredCustomModelData());
            section.set("marketDisplayName", definition.marketDisplayName());
            section.set("categoryId", definition.categoryId());

            yaml.save(customItemsFile.toFile());
            return definition;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write custom item definition to " + customItemsFile, exception);
        }
    }

    private void ensureFileExists() throws IOException {
        Path parent = customItemsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(customItemsFile)) {
            Files.writeString(customItemsFile, "items:" + System.lineSeparator());
        }
    }

    private String storageKeyFor(String itemType) {
        String sanitized = itemType.toLowerCase().replaceAll("[^a-z0-9_-]+", "_");
        return sanitized.isBlank() ? "custom_item" : sanitized;
    }
}
