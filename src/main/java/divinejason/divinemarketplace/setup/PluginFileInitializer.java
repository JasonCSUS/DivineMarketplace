package divinejason.divinemarketplace.setup;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Creates the plugin folder structure and any missing default files on startup.
 *
 * Important policy:
 * - bundled files in src/main/resources are the exact shipped defaults
 * - built-in category mapping files are copied exactly from bundled defaults
 * - blank category files are only generated for custom categories added later
 * - validation of built-in category coverage runs only when bundled defaults are
 *   being copied during bootstrap
 */
public final class PluginFileInitializer {

    private final JavaPlugin plugin;

    public PluginFileInitializer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Logger logger = plugin.getLogger();
        Path dataFolder = plugin.getDataFolder().toPath();

        try {
            for (Path requiredDirectory : PluginDirectoryLayout.requiredDirectories(dataFolder)) {
                ensureDirectory(requiredDirectory);
            }

            boolean copiedCategoryConfig = ensureBundledFile("defaults/category_config.yml", PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder), logger);

            ensureBundledFile("config.yml", PluginDirectoryLayout.resolveConfigFile(dataFolder), logger);
            ensureBundledFile("defaults/menu.yml", PluginDirectoryLayout.resolveMenuConfigFile(dataFolder), logger);
            ensureBundledFile("defaults/custom/custom_items.yml", PluginDirectoryLayout.resolveCustomItemsFile(dataFolder), logger);
            ensureBundledFile("defaults/custom/custom_enchants.yml", PluginDirectoryLayout.resolveCustomEnchantsFile(dataFolder), logger);
            ensureBundledFile("defaults/permissions.txt", PluginDirectoryLayout.resolvePermissionsFile(dataFolder), logger);

            for (Path logFile : PluginDirectoryLayout.requiredTextLogFiles(dataFolder)) {
                ensureTextFile(logFile, "");
            }

            boolean copiedAnyBundledCategoryDefaults = ensureCategoryFiles(dataFolder, logger);

            if (copiedCategoryConfig || copiedAnyBundledCategoryDefaults) {
                validateBundledDefaultCategoryCoverage(dataFolder, logger);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize DivineMarketplace files.", exception);
        }
    }

    /**
     * Ensures every configured category has a live category file.
     *
     * Built-in categories:
     * - copy the bundled exact default file from resources/defaults/categories/
     *
     * Custom categories:
     * - generate a blank file with categoryId and empty items list
     */
    private boolean ensureCategoryFiles(Path dataFolder, Logger logger) throws IOException {
        boolean copiedAnyBundledCategoryDefaults = false;

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
        );

        ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
        if (categoriesSection == null) {
            logger.warning("category_config.yml has no categories section; no category mapping files were generated.");
            return false;
        }

        Set<String> categoryIds = new LinkedHashSet<>(categoriesSection.getKeys(false));
        for (String categoryId : categoryIds) {
            Path categoryFile = PluginDirectoryLayout.resolveCategoryFile(dataFolder, categoryId);
            if (Files.exists(categoryFile)) {
                continue;
            }

            String bundledResource = PluginDirectoryLayout.bundledCategoryResource(categoryId);
            if (hasBundledResource(bundledResource)) {
                if (ensureBundledFile(bundledResource, categoryFile, logger)) {
                    copiedAnyBundledCategoryDefaults = true;
                }
            } else {
                createBlankCategoryFileIfMissing(categoryFile, categoryId);
            }
        }

        return copiedAnyBundledCategoryDefaults;
    }

    private void validateBundledDefaultCategoryCoverage(Path dataFolder, Logger logger) {
        try {
            YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                    PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
            );

            ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
            if (categoriesSection == null) {
                return;
            }

            Set<Material> assigned = new LinkedHashSet<>();
            Set<Material> duplicates = new LinkedHashSet<>();
            List<String> invalidEntries = new ArrayList<>();

            for (String categoryId : categoriesSection.getKeys(false)) {
                String bundledResource = PluginDirectoryLayout.bundledCategoryResource(categoryId);
                if (!hasBundledResource(bundledResource)) {
                    continue;
                }

                Path liveCategoryFile = PluginDirectoryLayout.resolveCategoryFile(dataFolder, categoryId);
                YamlConfiguration categoryYaml = YamlConfiguration.loadConfiguration(liveCategoryFile.toFile());

                for (String rawMaterialName : categoryYaml.getStringList("items")) {
                    Material material = Material.matchMaterial(rawMaterialName);
                    if (material == null) {
                        invalidEntries.add(categoryId + ":" + rawMaterialName);
                        continue;
                    }

                    if (!assigned.add(material)) {
                        duplicates.add(material);
                    }
                }
            }

            List<String> uncategorized = new ArrayList<>();
            for (Material material : Material.values()) {
                if (shouldSkipVanillaCoverageValidation(material)) {
                    continue;
                }
                if (!assigned.contains(material)) {
                    uncategorized.add(material.name());
                }
            }

            if (!invalidEntries.isEmpty()) {
                logger.warning(() -> "Built-in category defaults contain invalid material names: " + invalidEntries);
            }

            if (!duplicates.isEmpty()) {
                logger.warning(() -> "Built-in category defaults contain duplicate materials: " + duplicates);
            }

            if (!uncategorized.isEmpty()) {
                logger.warning(() -> "Built-in category defaults missed " + uncategorized.size() + " allowed vanilla materials. They will fall into unsorted until categorized.");
                logger.warning(() -> "Uncategorized materials: " + uncategorized);
            }
        } catch (Exception exception) {
            logger.warning(() -> "Failed to validate bundled default category coverage: " + exception.getMessage());
        }
    }

    private boolean shouldSkipVanillaCoverageValidation(Material material) {
        if (material.isLegacy() || !material.isItem()) {
            return true;
        }

        String name = material.name();

        if (name.endsWith("_SPAWN_EGG")) {
            return true;
        }

        return switch (name) {
            case "AIR",
                    "CAVE_AIR",
                    "VOID_AIR",
                    "BARRIER",
                    "LIGHT",
                    "DEBUG_STICK",
                    "COMMAND_BLOCK",
                    "CHAIN_COMMAND_BLOCK",
                    "REPEATING_COMMAND_BLOCK",
                    "COMMAND_BLOCK_MINECART",
                    "STRUCTURE_BLOCK",
                    "STRUCTURE_VOID",
                    "JIGSAW",
                    "KNOWLEDGE_BOOK",
                    "BEDROCK",
                    "END_PORTAL_FRAME",
                    "PETRIFIED_OAK_SLAB",
                    "PLAYER_HEAD",
                    "ZOMBIE_HEAD",
                    "CREEPER_HEAD",
                    "SKELETON_SKULL",
                    "WITHER_SKELETON_SKULL",
                    "DRAGON_HEAD",
                    "PIGLIN_HEAD",
                    "TRIAL_SPAWNER",
                    "SPAWNER",
                    "VAULT",
                    "REINFORCED_DEEPSLATE" -> true;
            default -> false;
        };
    }

    private void createBlankCategoryFileIfMissing(Path categoryFile, String categoryId) throws IOException {
        if (Files.exists(categoryFile)) {
            return;
        }
        String content = "categoryId: " + categoryId + System.lineSeparator() + "items:" + System.lineSeparator();
        ensureTextFile(categoryFile, content);
    }

    private boolean ensureBundledFile(String resourcePath, Path destination, Logger logger) throws IOException {
        if (Files.exists(destination)) {
            return false;
        }

        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing bundled resource: " + resourcePath);
            }

            ensureDirectory(destination.getParent());
            Files.copy(inputStream, destination);
            logger.info(() -> "Created default file: " + plugin.getDataFolder().toPath().relativize(destination));
            return true;
        }
    }

    private boolean hasBundledResource(String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            return inputStream != null;
        } catch (IOException ignored) {
            return false;
        }
    }


    private void ensureTextFile(Path path, String contents) throws IOException {
        if (Files.exists(path)) {
            return;
        }

        ensureDirectory(path.getParent());
        Files.writeString(path, contents);
    }

    private void ensureDirectory(Path path) throws IOException {
        if (path == null) {
            return;
        }
        Files.createDirectories(path);
    }
}
