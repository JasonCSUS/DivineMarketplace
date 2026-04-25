package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the live category mapping files into a simple in-memory material map.
 */
public final class DefaultCategoryResolver implements CategoryResolver {
    private final Path dataFolder;
    private final Map<Material, String> materialToCategoryId = new LinkedHashMap<>();

    public DefaultCategoryResolver(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath());
    }

    public DefaultCategoryResolver(Path dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        reload();
    }

    @Override
    public String resolveCategoryId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "unsorted";
        }
        return materialToCategoryId.getOrDefault(itemStack.getType(), "unsorted");
    }

    public void reload() {
        materialToCategoryId.clear();

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
        );

        ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
        if (categoriesSection == null) {
            return;
        }

        for (String categoryId : categoriesSection.getKeys(false)) {
            Path categoryFile = PluginDirectoryLayout.resolveCategoryFile(dataFolder, categoryId);
            YamlConfiguration categoryYaml = YamlConfiguration.loadConfiguration(categoryFile.toFile());

            for (String rawMaterialName : categoryYaml.getStringList("items")) {
                Material material = Material.matchMaterial(rawMaterialName);
                if (material != null) {
                    materialToCategoryId.put(material, categoryId);
                }
            }
        }
    }
}
