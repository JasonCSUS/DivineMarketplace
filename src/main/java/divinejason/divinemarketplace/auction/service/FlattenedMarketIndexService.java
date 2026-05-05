package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CategoryDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinition;
import divinejason.divinemarketplace.auction.model.CustomItemDefinitionState;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMarketIndexStore;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

/**
 * SQLite-backed flattened market index service.
 *
 * Runtime item/category reads come from SQLite once the market_index table has
 * been seeded. category_config.yml remains the authoritative source for
 * top-level browse presentation: display names, icons, and category order. That
 * keeps player-facing menus readable without coupling category labels to the
 * internal category id used by commands, storage, and admin tools.
 */
public final class FlattenedMarketIndexService {
    private final Path dataFolder;
    private final Logger logger;
    private final SQLiteMarketIndexStore marketIndexStore;

    private final Map<String, FlattenedMarketIndexEntry> byMarketKey = new LinkedHashMap<>();
    private final Map<String, String> normalizedDisplayNameToMarketKey = new LinkedHashMap<>();
    private final Map<String, CategoryDefinition> categoryDefinitionsById = new LinkedHashMap<>();

    public FlattenedMarketIndexService(JavaPlugin plugin, SQLiteMarketIndexStore marketIndexStore) {
        this.dataFolder = Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath();
        this.logger = plugin.getLogger();
        this.marketIndexStore = Objects.requireNonNull(marketIndexStore, "marketIndexStore");
        reload();
    }

    public synchronized void reload() {
        reloadCategoryDefinitions();

        Map<String, FlattenedMarketIndexEntry> loaded = marketIndexStore.loadAll();
        if (loaded.isEmpty()) {
            List<FlattenedMarketIndexEntry> seeded = buildSeedEntriesFromDefinitions();
            marketIndexStore.replaceAll(seeded);
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

    public synchronized List<FlattenedMarketIndexEntry> getFlaggedEntries() {
        return byMarketKey.values().stream()
                .filter(entry -> "unsorted".equalsIgnoreCase(entry.categoryId()))
                .sorted(Comparator.comparing(FlattenedMarketIndexEntry::marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized boolean updateCategory(String marketKey, String categoryId) {
        FlattenedMarketIndexEntry existing = byMarketKey.get(marketKey);
        if (existing == null) {
            return false;
        }

        Map<String, FlattenedMarketIndexEntry> all = new LinkedHashMap<>(byMarketKey);
        all.put(marketKey, new FlattenedMarketIndexEntry(
                existing.recordType(),
                existing.marketKey(),
                existing.marketDisplayName(),
                normalizeCategory(categoryId),
                existing.itemType(),
                existing.requiredMaterial(),
                existing.requiredCustomModelData(),
                existing.definitionState()
        ));
        marketIndexStore.replaceAll(all.values());
        reload();
        return true;
    }

    public synchronized boolean renameCustomMarket(String marketKey, String marketDisplayName) {
        FlattenedMarketIndexEntry existing = byMarketKey.get(marketKey);
        if (existing == null || !existing.isCustom()) {
            return false;
        }

        Map<String, FlattenedMarketIndexEntry> all = new LinkedHashMap<>(byMarketKey);
        all.put(marketKey, new FlattenedMarketIndexEntry(
                existing.recordType(),
                existing.marketKey(),
                marketDisplayName,
                existing.categoryId(),
                existing.itemType(),
                existing.requiredMaterial(),
                existing.requiredCustomModelData(),
                existing.definitionState()
        ));
        marketIndexStore.replaceAll(all.values());
        reload();
        return true;
    }

    public synchronized void upsertCustomDefinition(CustomItemDefinition definition) {
        Map<String, FlattenedMarketIndexEntry> all = new LinkedHashMap<>(byMarketKey);
        all.put(definition.itemType(), new FlattenedMarketIndexEntry(
                "CUSTOM",
                definition.itemType(),
                definition.marketDisplayName(),
                normalizeCategory(definition.categoryId()),
                definition.itemType(),
                definition.requiredMaterial(),
                definition.requiredCustomModelData(),
                definition.state()
        ));
        marketIndexStore.replaceAll(all.values());
        reload();
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

        YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(
                PluginDirectoryLayout.resolveCategoryConfigFile(dataFolder).toFile()
        );

        ConfigurationSection categoriesSection = categoryConfig.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String categoryId : categoriesSection.getKeys(false)) {
                Path categoryFile = PluginDirectoryLayout.resolveCategoryFile(dataFolder, categoryId);
                YamlConfiguration categoryYaml = YamlConfiguration.loadConfiguration(categoryFile.toFile());

                for (String rawMaterialName : categoryYaml.getStringList("items")) {
                    Material material = Material.matchMaterial(rawMaterialName);
                    if (material == null) {
                        invalidEntries.add(categoryId + ":" + rawMaterialName);
                        continue;
                    }

                    if (vanillaAssignments.containsKey(material)) {
                        duplicates.add(material);
                    }

                    vanillaAssignments.put(material, normalizeCategory(categoryId));
                }
            }
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
}
