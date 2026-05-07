package divinejason.divinemarketplace.setup;


/*
 * File role: Creates missing plugin folders/default files on first install without overwriting server-owner edits.
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Creates the plugin folder structure and any missing default files on startup.
 *
 * Important policy:
 * - bundled files in src/main/resources are seed/default sources
 * - category_config.yml is copied once and remains owner-editable
 * - bundled category YAML files are internal seed data only; they are converted
 *   into one server-side categories.csv file instead of being copied as many
 *   separate files
 * - existing server-owner files are never overwritten by startup initialization
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

            boolean generatedCategoriesCsv = ensureCategoriesCsv(dataFolder, logger);

            if (copiedCategoryConfig || generatedCategoriesCsv) {
                validateCategoriesCsvCoverage(dataFolder, logger);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize DivineMarketplace files.", exception);
        }
    }

    /**
     * Creates categories.csv when it is missing.
     *
     * Migration order matters:
     * - if an older alpha install already has plugins/DivineMarketplace/categories/*.yml,
     *   convert those live files first so server-owner category edits are preserved
     * - otherwise build the CSV from bundled defaults/categories/*.yml resources
     *
     * The legacy folder is left alone after conversion. Removing it automatically
     * would be risky because it could contain owner notes or staged edits.
     */
    private boolean ensureCategoriesCsv(Path dataFolder, Logger logger) throws IOException {
        Path csvFile = PluginDirectoryLayout.resolveCategoriesCsvFile(dataFolder);
        if (Files.exists(csvFile)) {
            return false;
        }

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
        );

        ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
        if (categoriesSection == null) {
            logger.warning("category_config.yml has no categories section; generated categories.csv with only a header.");
            writeCategoriesCsv(csvFile, Map.of());
            return true;
        }

        Map<String, List<String>> categoryItems = loadCategoryItemsFromLegacyOrBundledDefaults(dataFolder, categoriesSection, logger);
        writeCategoriesCsv(csvFile, categoryItems);
        logger.info(() -> "Generated editable category mapping file: " + dataFolder.relativize(csvFile));
        return true;
    }

    private Map<String, List<String>> loadCategoryItemsFromLegacyOrBundledDefaults(
            Path dataFolder,
            ConfigurationSection categoriesSection,
            Logger logger
    ) {
        Map<String, List<String>> categoryItems = new LinkedHashMap<>();

        for (String rawCategoryId : categoriesSection.getKeys(false)) {
            String categoryId = normalizeCategory(rawCategoryId);
            Path legacyFile = PluginDirectoryLayout.resolveLegacyCategoryFile(dataFolder, categoryId);

            if (Files.exists(legacyFile)) {
                YamlConfiguration legacyYaml = YamlConfiguration.loadConfiguration(legacyFile.toFile());
                categoryItems.put(categoryId, normalizeMaterialNames(legacyYaml.getStringList("items")));
                continue;
            }

            String bundledResource = PluginDirectoryLayout.bundledCategoryResource(categoryId);
            if (hasBundledResource(bundledResource)) {
                YamlConfiguration bundledYaml = loadBundledYaml(bundledResource);
                categoryItems.put(categoryId, normalizeMaterialNames(bundledYaml.getStringList("items")));
            } else {
                categoryItems.put(categoryId, List.of());
                logger.info(() -> "No bundled default category list for custom category " + categoryId + "; categories.csv will start with no rows for it.");
            }
        }

        return categoryItems;
    }

    private void writeCategoriesCsv(Path csvFile, Map<String, List<String>> categoryItems) throws IOException {
        String newline = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("# Editable vanilla item category map for DivineMarketplace.").append(newline);
        builder.append("# category_config.yml controls display names, icons, and ordering.").append(newline);
        builder.append("# default_prices.csv is intentionally separate from this category mapping file.").append(newline);
        builder.append("material,category").append(newline);

        for (Map.Entry<String, List<String>> entry : categoryItems.entrySet()) {
            String categoryId = normalizeCategory(entry.getKey());
            for (String materialName : entry.getValue()) {
                if (materialName.isBlank()) {
                    continue;
                }
                builder.append(escapeCsv(materialName)).append(',').append(escapeCsv(categoryId)).append(newline);
            }
        }

        ensureTextFile(csvFile, builder.toString());
    }

    private List<String> normalizeMaterialNames(List<String> rawNames) {
        List<String> normalized = new ArrayList<>();
        for (String rawName : rawNames) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            normalized.add(rawName.trim().toUpperCase(java.util.Locale.ROOT));
        }
        return normalized;
    }

    private void validateCategoriesCsvCoverage(Path dataFolder, Logger logger) {
        try {
            Path csvFile = PluginDirectoryLayout.resolveCategoriesCsvFile(dataFolder);
            if (!Files.exists(csvFile)) {
                return;
            }

            Set<Material> assigned = new LinkedHashSet<>();
            Set<Material> duplicates = new LinkedHashSet<>();
            List<String> invalidEntries = new ArrayList<>();

            for (CsvRow row : readCategoriesCsvRows(csvFile)) {
                Material material = Material.matchMaterial(row.material());
                if (material == null) {
                    invalidEntries.add(row.category() + ":" + row.material());
                    continue;
                }

                if (!assigned.add(material)) {
                    duplicates.add(material);
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
                logger.warning(() -> "categories.csv contains invalid material names: " + invalidEntries);
            }

            if (!duplicates.isEmpty()) {
                logger.warning(() -> "categories.csv contains duplicate material assignments. Later rows win during market-index seeding: " + duplicates);
            }

            if (!uncategorized.isEmpty()) {
                logger.warning(() -> "categories.csv missed " + uncategorized.size() + " allowed vanilla materials. They will fall into unsorted until categorized.");
                logger.warning(() -> "Uncategorized materials: " + uncategorized);
            }
        } catch (Exception exception) {
            logger.warning(() -> "Failed to validate categories.csv coverage: " + exception.getMessage());
        }
    }

    private List<CsvRow> readCategoriesCsvRows(Path csvFile) throws IOException {
        List<CsvRow> rows = new ArrayList<>();
        boolean headerSeen = false;

        for (String rawLine : Files.readAllLines(csvFile, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            List<String> columns = parseCsvLine(rawLine);
            if (columns.size() < 2) {
                continue;
            }

            if (!headerSeen && "material".equalsIgnoreCase(columns.get(0).trim()) && "category".equalsIgnoreCase(columns.get(1).trim())) {
                headerSeen = true;
                continue;
            }
            headerSeen = true;

            String material = columns.get(0).trim();
            String category = normalizeCategory(columns.get(1));
            if (!material.isBlank()) {
                rows.add(new CsvRow(material, category));
            }
        }

        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (character == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        columns.add(current.toString());
        return columns;
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

    private YamlConfiguration loadBundledYaml(String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bundled YAML resource: " + resourcePath, exception);
        }
    }

    private String normalizeCategory(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? "unsorted" : categoryId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!mustQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void ensureTextFile(Path path, String contents) throws IOException {
        if (Files.exists(path)) {
            return;
        }

        ensureDirectory(path.getParent());
        Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    private void ensureDirectory(Path path) throws IOException {
        if (path == null) {
            return;
        }
        Files.createDirectories(path);
    }

    private record CsvRow(String material, String category) {
    }
}
