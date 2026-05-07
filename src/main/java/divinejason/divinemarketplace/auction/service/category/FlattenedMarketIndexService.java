package divinejason.divinemarketplace.auction.service.category;

/*
 * Layer : service
 * Owns  : flattened market index behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Contains marketplace service logic for flattened market index service.
 */
import divinejason.divinemarketplace.auction.model.CategoryDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketIndexStore;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SQLite-backed flattened market index service.
 *
 * Runtime item/category reads come from SQLite once the market_index table has
 * been seeded. categories.csv is the editable vanilla item-to-category seed
 * file, while category_config.yml remains the authoritative source for
 * top-level browse presentation: display names, icons, and category order. That
 * keeps player-facing menus readable without coupling category labels to the
 * internal category id used by commands, storage, and admin tools.
 */
public final class FlattenedMarketIndexService {
    private final Path dataFolder;
    private final Logger logger;
    private final SQLiteMarketIndexStore marketIndexStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;

    private final Map<String, FlattenedMarketIndexEntry> byMarketKey = new LinkedHashMap<>();
    private final Map<String, String> normalizedDisplayNameToMarketKey = new LinkedHashMap<>();
    private final Map<String, CategoryDefinition> categoryDefinitionsById = new LinkedHashMap<>();

    public FlattenedMarketIndexService(JavaPlugin plugin, SQLiteMarketIndexStore marketIndexStore, SQLiteWriteBehindQueue writeBehindQueue) {
        this.dataFolder = Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath();
        this.logger = plugin.getLogger();
        this.marketIndexStore = Objects.requireNonNull(marketIndexStore, "marketIndexStore");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
    }

    /** Initial load: reads YAML config and SQL market index, seeds the table if empty. */
    public synchronized void loadFromStorage() {
        reloadCategoryDefinitions();

        Map<String, FlattenedMarketIndexEntry> loaded = marketIndexStore.loadAll();
        if (loaded.isEmpty()) {
            List<FlattenedMarketIndexEntry> seeded = buildSeedEntriesFromDefinitions();
            marketIndexStore.adminReplaceAll(seeded);
            loaded = marketIndexStore.loadAll();
        } else if (synchronizeVanillaCategoriesFromCsv(loaded)) {
            marketIndexStore.adminReplaceAll(loaded.values());
            loaded = marketIndexStore.loadAll();
        }

        byMarketKey.clear();
        normalizedDisplayNameToMarketKey.clear();

        for (FlattenedMarketIndexEntry entry : loaded.values()) {
            byMarketKey.put(entry.marketKey(), entry);
            if (entry.marketDisplayName() != null && !entry.marketDisplayName().isBlank()) {
                normalizedDisplayNameToMarketKey.put(normalize(entry.marketDisplayName()), entry.marketKey());
            }
        }
    }

    public synchronized void reload() {
        loadFromStorage();
    }

    public synchronized FlattenedMarketIndexEntry getByMarketKey(String marketKey) {
        return byMarketKey.get(marketKey);
    }

    public synchronized String resolveMarketKeyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        if (byMarketKey.containsKey(token)) {
            return token;
        }

        String normalized = normalize(token);
        String byDisplay = normalizedDisplayNameToMarketKey.get(normalized);
        if (byDisplay != null) {
            return byDisplay;
        }

        for (FlattenedMarketIndexEntry entry : byMarketKey.values()) {
            if (entry.marketKey().toLowerCase(Locale.ROOT).startsWith(normalized)
                    || entry.marketDisplayName().toLowerCase(Locale.ROOT).startsWith(normalized)) {
                return entry.marketKey();
            }
        }

        return null;
    }

    public synchronized Collection<FlattenedMarketIndexEntry> entries() {
        return List.copyOf(byMarketKey.values());
    }

    public synchronized Set<String> getPlayerDisplayNames() {
        Set<String> out = new LinkedHashSet<>();
        for (FlattenedMarketIndexEntry entry : byMarketKey.values()) {
            if (!"unsorted".equalsIgnoreCase(entry.categoryId())) {
                out.add(entry.marketDisplayName());
            }
        }
        return out;
    }

    public synchronized Set<String> getAdminTokens() {
        Set<String> out = new LinkedHashSet<>();
        for (FlattenedMarketIndexEntry entry : byMarketKey.values()) {
            out.add(entry.marketKey());
            out.add(entry.marketDisplayName());
        }
        return out;
    }

    public synchronized List<String> getKnownMarketKeys() {
        return new ArrayList<>(byMarketKey.keySet());
    }

    public synchronized List<String> getCategoryIds(boolean includeUnsorted) {
        Set<String> ids = new LinkedHashSet<>();

        for (String categoryId : categoryDefinitionsById.keySet()) {
            if (!includeUnsorted && "unsorted".equalsIgnoreCase(categoryId)) {
                continue;
            }
            ids.add(categoryId);
        }

        for (FlattenedMarketIndexEntry entry : byMarketKey.values()) {
            if (!includeUnsorted && "unsorted".equalsIgnoreCase(entry.categoryId())) {
                continue;
            }
            ids.add(entry.categoryId());
        }

        if (includeUnsorted && ids.removeIf(id -> "unsorted".equalsIgnoreCase(id))) {
            ids.add("unsorted");
        }
        return new ArrayList<>(ids);
    }

    public synchronized CategoryDefinition getCategoryDefinition(String categoryId) {
        String normalized = normalizeCategory(categoryId);
        CategoryDefinition definition = categoryDefinitionsById.get(normalized);
        if (definition != null) {
            return definition;
        }
        return new CategoryDefinition(normalized, humanizeToken(normalized), "CHEST");
    }

    public synchronized String getCategoryDisplayName(String categoryId) {
        return getCategoryDefinition(categoryId).displayName();
    }

    public synchronized String getCategoryIconKey(String categoryId) {
        return getCategoryDefinition(categoryId).iconKey();
    }

    /**
     * Applies owner edits from categories.csv to existing vanilla market-index rows.
     *
     * The SQLite market_index table is still the runtime source of truth, but
     * vanilla category assignments are intentionally owner-editable as text.
     * Reload therefore replays categories.csv over vanilla rows while leaving
     * custom item definitions and admin-discovered custom entries untouched.
     */
    private boolean synchronizeVanillaCategoriesFromCsv(Map<String, FlattenedMarketIndexEntry> loaded) {
        Path categoriesCsv = PluginDirectoryLayout.resolveCategoriesCsvFile(dataFolder);
        if (!Files.exists(categoriesCsv)) {
            return false;
        }

        Map<Material, String> assignments = new LinkedHashMap<>();
        for (CsvCategoryAssignment assignment : readCategoryAssignments(categoriesCsv)) {
            Material material = Material.matchMaterial(assignment.materialName());
            if (material == null || shouldSkipSeedVanillaMaterial(material)) {
                continue;
            }
            assignments.put(material, normalizeCategory(assignment.categoryId()));
        }

        boolean changed = false;
        Set<Material> presentMaterials = new LinkedHashSet<>();
        for (FlattenedMarketIndexEntry entry : new ArrayList<>(loaded.values())) {
            if (!entry.isVanilla() || entry.requiredMaterial() == null) {
                continue;
            }

            Material material = entry.requiredMaterial();
            presentMaterials.add(material);
            String configuredCategory = assignments.get(material);
            if (configuredCategory == null || configuredCategory.equalsIgnoreCase(entry.categoryId())) {
                continue;
            }

            loaded.put(entry.marketKey(), new FlattenedMarketIndexEntry(
                    entry.recordType(),
                    entry.marketKey(),
                    entry.marketDisplayName(),
                    configuredCategory,
                    entry.itemType(),
                    entry.requiredMaterial(),
                    entry.requiredCustomModelData(),
                    entry.definitionState()
            ));
            changed = true;
        }

        for (Map.Entry<Material, String> assignment : assignments.entrySet()) {
            Material material = assignment.getKey();
            if (presentMaterials.contains(material)) {
                continue;
            }
            FlattenedMarketIndexEntry created = new FlattenedMarketIndexEntry(
                    "VANILLA",
                    "vanilla:" + material.name().toLowerCase(Locale.ROOT),
                    humanizeToken(material.name()),
                    assignment.getValue(),
                    "",
                    material,
                    null,
                    CustomItemDefinitionState.CONFIRMED
            );
            loaded.put(created.marketKey(), created);
            changed = true;
        }

        return changed;
    }

    public synchronized List<FlattenedMarketIndexEntry> getFlaggedEntries() {
        return byMarketKey.values().stream()
                .filter(entry -> "unsorted".equalsIgnoreCase(entry.categoryId()))
                .sorted(Comparator.comparing(FlattenedMarketIndexEntry::marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Admin operation: updates the category for one market key and enqueues the write.
     * Memory is updated immediately; SQLite write is queued for async durability.
     */
    public synchronized boolean updateCategory(String marketKey, String categoryId) {
        FlattenedMarketIndexEntry existing = byMarketKey.get(marketKey);
        if (existing == null) {
            return false;
        }

        String normalizedCategoryId = normalizeCategory(categoryId);
        if (existing.isVanilla() && existing.requiredMaterial() != null) {
            writeVanillaCategoryAssignment(existing.requiredMaterial(), normalizedCategoryId);
        }

        FlattenedMarketIndexEntry updated = new FlattenedMarketIndexEntry(
                existing.recordType(),
                existing.marketKey(),
                existing.marketDisplayName(),
                normalizedCategoryId,
                existing.itemType(),
                existing.requiredMaterial(),
                existing.requiredCustomModelData(),
                existing.definitionState()
        );
        byMarketKey.put(marketKey, updated);
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("market index update category " + marketKey)
                .add(marketIndexStore.putMutation(updated))
                .build());
        return true;
    }

    /**
     * Admin operation: renames a custom market and enqueues the write.
     * Memory is updated immediately; SQLite write is queued for async durability.
     */
    public synchronized boolean renameCustomMarket(String marketKey, String marketDisplayName) {
        FlattenedMarketIndexEntry existing = byMarketKey.get(marketKey);
        if (existing == null || !existing.isCustom()) {
            return false;
        }

        FlattenedMarketIndexEntry updated = new FlattenedMarketIndexEntry(
                existing.recordType(),
                existing.marketKey(),
                marketDisplayName,
                existing.categoryId(),
                existing.itemType(),
                existing.requiredMaterial(),
                existing.requiredCustomModelData(),
                existing.definitionState()
        );
        byMarketKey.put(marketKey, updated);
        if (existing.marketDisplayName() != null && !existing.marketDisplayName().isBlank()) {
            normalizedDisplayNameToMarketKey.remove(normalize(existing.marketDisplayName()));
        }
        if (marketDisplayName != null && !marketDisplayName.isBlank()) {
            normalizedDisplayNameToMarketKey.put(normalize(marketDisplayName), marketKey);
        }
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("market index rename " + marketKey)
                .add(marketIndexStore.putMutation(updated))
                .build());
        return true;
    }

    /**
     * Admin/discovery operation: upserts a custom item definition and enqueues the write.
     * Memory is updated immediately; SQLite write is queued for async durability.
     */
    public synchronized void upsertCustomDefinition(CustomItemDefinition definition) {
        FlattenedMarketIndexEntry entry = new FlattenedMarketIndexEntry(
                "CUSTOM",
                definition.itemType(),
                definition.marketDisplayName(),
                normalizeCategory(definition.categoryId()),
                definition.itemType(),
                definition.requiredMaterial(),
                definition.requiredCustomModelData(),
                definition.state()
        );
        FlattenedMarketIndexEntry existing = byMarketKey.get(definition.itemType());
        if (existing != null && existing.marketDisplayName() != null && !existing.marketDisplayName().isBlank()) {
            normalizedDisplayNameToMarketKey.remove(normalize(existing.marketDisplayName()));
        }
        byMarketKey.put(entry.marketKey(), entry);
        if (entry.marketDisplayName() != null && !entry.marketDisplayName().isBlank()) {
            normalizedDisplayNameToMarketKey.put(normalize(entry.marketDisplayName()), entry.marketKey());
        }
        writeBehindQueue.enqueue(SQLiteWriteBatch.builder("market index upsert custom " + definition.itemType())
                .add(marketIndexStore.putMutation(entry))
                .build());
    }

    private void reloadCategoryDefinitions() {
        categoryDefinitionsById.clear();

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
        );
        ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
        if (categoriesSection == null) {
            categoryDefinitionsById.put("unsorted", new CategoryDefinition("unsorted", "Unsorted", "BARRIER"));
            return;
        }

        for (String rawCategoryId : categoriesSection.getKeys(false)) {
            String categoryId = normalizeCategory(rawCategoryId);
            ConfigurationSection section = categoriesSection.getConfigurationSection(rawCategoryId);
            String displayName = section == null ? "" : blankToEmpty(section.getString("displayName"));
            String icon = section == null ? "" : blankToEmpty(section.getString("icon"));
            categoryDefinitionsById.put(categoryId, new CategoryDefinition(
                    categoryId,
                    displayName.isBlank() ? humanizeToken(categoryId) : displayName,
                    icon.isBlank() ? fallbackCategoryIcon(categoryId) : icon
            ));
        }

        categoryDefinitionsById.putIfAbsent("unsorted", new CategoryDefinition("unsorted", "Unsorted", "BARRIER"));
    }

    private String normalizeCategory(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? "unsorted" : categoryId.trim().toLowerCase(Locale.ROOT);
    }

    private List<FlattenedMarketIndexEntry> buildSeedEntriesFromDefinitions() {
        List<FlattenedMarketIndexEntry> rows = new ArrayList<>();
        Map<Material, String> vanillaAssignments = new LinkedHashMap<>();
        Set<Material> duplicates = new LinkedHashSet<>();
        List<String> invalidEntries = new ArrayList<>();

        Path categoriesCsv = PluginDirectoryLayout.resolveCategoriesCsvFile(dataFolder);
        if (Files.exists(categoriesCsv)) {
            for (CsvCategoryAssignment assignment : readCategoryAssignments(categoriesCsv)) {
                Material material = Material.matchMaterial(assignment.materialName());
                if (material == null) {
                    invalidEntries.add(assignment.categoryId() + ":" + assignment.materialName());
                    continue;
                }

                if (vanillaAssignments.containsKey(material)) {
                    duplicates.add(material);
                }

                vanillaAssignments.put(material, normalizeCategory(assignment.categoryId()));
            }
        } else {
            logger.warning("categories.csv is missing. Vanilla market index seed will place uncategorized items into unsorted until the file is restored.");
        }

        if (!invalidEntries.isEmpty()) {
            logger.warning(() -> "Market index seed skipped invalid material names: " + invalidEntries);
        }
        if (!duplicates.isEmpty()) {
            logger.warning(() -> "Market index seed saw duplicate material assignments: " + duplicates);
        }

        for (Material material : Material.values()) {
            if (shouldSkipSeedVanillaMaterial(material)) {
                continue;
            }

            String categoryId = vanillaAssignments.getOrDefault(material, "unsorted");
            rows.add(new FlattenedMarketIndexEntry(
                    "VANILLA",
                    "vanilla:" + material.name().toLowerCase(Locale.ROOT),
                    humanizeToken(material.name()),
                    categoryId,
                    "",
                    material,
                    null,
                    CustomItemDefinitionState.CONFIRMED
            ));
        }

        YamlConfiguration customItemsYaml = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCustomItemsFile(dataFolder).toFile()
        );

        ConfigurationSection itemsSection = customItemsYaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String storageKey : itemsSection.getKeys(false)) {
                ConfigurationSection section = itemsSection.getConfigurationSection(storageKey);
                if (section == null) {
                    continue;
                }

                String itemType = blankToEmpty(section.getString("itemType"));
                String materialName = blankToEmpty(section.getString("requiredMaterial"));
                String marketDisplayName = blankToEmpty(section.getString("marketDisplayName"));
                String categoryId = blankToEmpty(section.getString("categoryId", "unsorted"));
                Float requiredCustomModelData = section.contains("requiredCustomModelData")
                        ? (float) section.getDouble("requiredCustomModelData")
                        : null;

                if (itemType.isBlank() || materialName.isBlank() || marketDisplayName.isBlank()) {
                    continue;
                }

                Material requiredMaterial = Material.matchMaterial(materialName);
                if (requiredMaterial == null) {
                    logger.warning(() -> "Skipped custom item with invalid requiredMaterial in custom_items.yml: " + itemType + " -> " + materialName);
                    continue;
                }

                rows.add(new FlattenedMarketIndexEntry(
                        "CUSTOM",
                        itemType,
                        marketDisplayName,
                        normalizeCategory(categoryId),
                        itemType,
                        requiredMaterial,
                        requiredCustomModelData,
                        readCustomDefinitionState(section)
                ));
            }
        }

        return rows;
    }

    private List<CsvCategoryAssignment> readCategoryAssignments(Path categoriesCsv) {
        List<CsvCategoryAssignment> assignments = new ArrayList<>();
        boolean headerSeen = false;

        try {
            for (String rawLine : Files.readAllLines(categoriesCsv, StandardCharsets.UTF_8)) {
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

                String materialName = columns.get(0).trim();
                String categoryId = normalizeCategory(columns.get(1));
                if (!materialName.isBlank()) {
                    assignments.add(new CsvCategoryAssignment(materialName, categoryId));
                }
            }
        } catch (Exception exception) {
            logger.warning(() -> "Failed to read categories.csv; vanilla items will seed as unsorted until the file is fixed: " + exception.getMessage());
        }

        return assignments;
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

    /**
     * Keeps /market sort durable for vanilla materials now that categories.csv is
     * the owner-editable source for vanilla item membership. Custom items remain
     * SQLite-backed because their definitions are created through discovery/admin
     * flows rather than the vanilla category seed file.
     */
    private void writeVanillaCategoryAssignment(Material material, String categoryId) {
        Path categoriesCsv = PluginDirectoryLayout.resolveCategoriesCsvFile(dataFolder);
        Map<Material, String> assignments = new LinkedHashMap<>();

        for (CsvCategoryAssignment assignment : readCategoryAssignments(categoriesCsv)) {
            Material parsedMaterial = Material.matchMaterial(assignment.materialName());
            if (parsedMaterial == null || shouldSkipSeedVanillaMaterial(parsedMaterial)) {
                continue;
            }
            assignments.put(parsedMaterial, normalizeCategory(assignment.categoryId()));
        }

        assignments.put(material, normalizeCategory(categoryId));

        String newline = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("# Editable vanilla item category map for DivineMarketplace.").append(newline);
        builder.append("# category_config.yml controls display names, icons, and ordering.").append(newline);
        builder.append("# default_prices.csv is intentionally separate from this category mapping file.").append(newline);
        builder.append("material,category").append(newline);

        for (Map.Entry<Material, String> entry : assignments.entrySet()) {
            builder.append(entry.getKey().name()).append(',').append(normalizeCategory(entry.getValue())).append(newline);
        }

        try {
            Files.writeString(categoriesCsv, builder.toString(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            logger.warning(() -> "Updated SQLite category for " + material.name() + " but failed to update categories.csv: " + exception.getMessage());
        }
    }

    private CustomItemDefinitionState readCustomDefinitionState(ConfigurationSection section) {
        CustomItemDefinitionState state = CustomItemDefinitionState.fromStoredValue(section.getString("state"));
        return state == null ? CustomItemDefinitionState.CONFIRMED : state;
    }

    private boolean shouldSkipSeedVanillaMaterial(Material material) {
        if (material.isLegacy() || !material.isItem()) {
            return true;
        }

        String name = material.name();
        if (name.endsWith("_SPAWN_EGG")) {
            return true;
        }

        return switch (name) {
            case "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "LIGHT", "DEBUG_STICK",
                    "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
                    "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "KNOWLEDGE_BOOK", "BEDROCK",
                    "END_PORTAL_FRAME", "PETRIFIED_OAK_SLAB", "PLAYER_HEAD", "ZOMBIE_HEAD",
                    "CREEPER_HEAD", "SKELETON_SKULL", "WITHER_SKELETON_SKULL", "DRAGON_HEAD",
                    "PIGLIN_HEAD", "TRIAL_SPAWNER", "SPAWNER", "VAULT", "REINFORCED_DEEPSLATE" -> true;
            default -> false;
        };
    }

    private String fallbackCategoryIcon(String categoryId) {
        return switch (normalizeCategory(categoryId)) {
            case "building_blocks" -> "STONE";
            case "decorative_blocks" -> "FLOWER_POT";
            case "tools" -> "DIAMOND_PICKAXE";
            case "weapons" -> "DIAMOND_SWORD";
            case "armor" -> "DIAMOND_CHESTPLATE";
            case "enchanted_books" -> "ENCHANTED_BOOK";
            case "food" -> "COOKED_BEEF";
            case "farming" -> "WHEAT";
            case "ores" -> "DIAMOND_ORE";
            case "redstone" -> "REDSTONE";
            case "unsorted" -> "BARRIER";
            default -> "CHEST";
        };
    }

    private String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String humanizeToken(String token) {
        String[] parts = token.toLowerCase(Locale.ROOT).split("[_\\s]+", -1);
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.isEmpty() ? token : builder.toString();
    }

    private record CsvCategoryAssignment(String materialName, String categoryId) {
    }
}
