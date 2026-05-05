package divinejason.divinemarketplace.config;


/*
 * File role: Loads main config values from YAML and applies defaults/clamps before services read them.
 */
import divinejason.divinemarketplace.auction.model.SortMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainConfigLoader {
    public MainConfig load(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.isFile()) {
            throw new IllegalStateException("config.yml was not found after bootstrap: " + configFile.getAbsolutePath());
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        MainConfig.Storage storage = new MainConfig.Storage(
                new MainConfig.Database(
                        config.getString("storage.database.sqliteFile", "data/market.db"),
                        config.getString("storage.database.modulePrefix", "market")
                ),
                new MainConfig.Limits(
                        positiveInt(config, "storage.limits.salesHistoryMaxMb", 50),
                        positiveInt(config, "storage.limits.adminSalesHistoryMaxMb", 25),
                        positiveInt(config, "storage.limits.adminListingsHistoryMaxMb", 25),
                        positiveInt(config, "storage.limits.adminClaimsHistoryMaxMb", 25),
                        positiveInt(config, "storage.limits.itemClaimsSoftMaxMb", 100)
                ),
                new MainConfig.Cleanup(positiveInt(config, "storage.cleanup.abandonedItemClaimDays", 30))
        );

        MainConfig.CustomItems customItems = new MainConfig.CustomItems(
                config.getBoolean("customItems.autoDiscoverUnknownItems", true),
                config.getBoolean("customItems.autoWriteDefinitionsImmediately", true),
                config.getString("customItems.defaultCategory", "unsorted"),
                config.getBoolean("customItems.useClonedItemForPreviewTemplate", true),
                config.getBoolean("customItems.requireAdminReviewForAllNewItems", true),
                config.getBoolean("customItems.highPriorityOnlyForUnsafeResolution", true)
        );

        MainConfig.CustomItemIdentity customItemIdentity = new MainConfig.CustomItemIdentity(
                new MainConfig.UnknownCustomModelData(
                        config.getBoolean("customItemIdentity.unknownCustomModelData.enabled", true),
                        config.getBoolean("customItemIdentity.unknownCustomModelData.autoCreateDefinition", true),
                        config.getString("customItemIdentity.unknownCustomModelData.category", "unsorted")
                ),
                new MainConfig.MetadataSnapshots(
                        config.getBoolean("customItemIdentity.metadataSnapshots.writeUnknownSnapshots", true),
                        config.getBoolean("customItemIdentity.metadataSnapshots.writeInspectRawSnapshots", true),
                        config.getString("customItemIdentity.metadataSnapshots.directory", "logs/custom_item_metadata")
                ),
                readCustomIdentityRules(config)
        );

        MainConfig.ListingPolicies listingPolicies = new MainConfig.ListingPolicies(
                new MainConfig.ListingPolicy(
                        positiveInt(config, "listingPolicies.default.maxListings", 15),
                        positiveInt(config, "listingPolicies.default.listingDurationDays", 7)
                ),
                readListingTiers(config)
        );

        MainConfig.Claims claims = new MainConfig.Claims(
                config.getBoolean("claims.claimMenuUsesSafeChunkRedemption", true),
                config.getBoolean("claims.shiftClickClaimsAsMuchAsSafelyFits", true),
                positiveInt(config, "claims.maxActiveItemClaims", 54)
        );

        MainConfig.Packages packagesConfig = new MainConfig.Packages(
                config.getString("packages.previewMode", "EXACT"),
                config.getBoolean("packages.keepExactPayloadInFileStorage", true),
                config.getBoolean("packages.keepExactPayloadCachedInMemory", false)
        );

        MainConfig.Market market = new MainConfig.Market(
                positiveInt(config, "market.recalcIntervalHours", 24),
                positiveInt(config, "market.perItemMinimumRecalcHours", 24),
                positiveInt(config, "market.recalcItemsPerRun", 5),
                positiveInt(config, "market.saleLookbackDays", 30),
                new MainConfig.Thresholds(
                        nonNegativeDouble(config, "market.thresholds.samePercent", 5.0),
                        nonNegativeDouble(config, "market.thresholds.smallAdjustmentPercent", 10.0),
                        nonNegativeDouble(config, "market.thresholds.mediumAdjustmentPercent", 15.0),
                        nonNegativeDouble(config, "market.thresholds.majorOverpricedPercent", 100.0),
                        nonNegativeDouble(config, "market.thresholds.majorUnderpricedPercent", 50.0)
                ),
                new MainConfig.Adjustment(
                        nonNegativeDouble(config, "market.adjustment.minimumPercent", 1.0),
                        nonNegativeDouble(config, "market.adjustment.maximumPercent", 50.0)
                ),
                new MainConfig.Trend(
                        nonNegativeDouble(config, "market.trend.fitnessThreshold", 0.60),
                        positiveInt(config, "market.trend.minimumSaleSamples", 3),
                        positiveInt(config, "market.trend.minimumListingSamples", 3)
                )
        );

        MainConfig.Search search = new MainConfig.Search(
                config.getBoolean("search.partialMatching", true),
                positiveInt(config, "search.minTokenLength", 2),
                positiveInt(config, "search.maxResultsPerPage", 20)
        );

        MainConfig.Admin admin = new MainConfig.Admin(
                config.getBoolean("admin.alertUnknownCustomItems", true),
                config.getBoolean("admin.alertUnknownCustomEnchants", true),
                config.getBoolean("admin.writeUnknownDefinitionsImmediately", true),
                config.getBoolean("admin.allowInGameDefinitionCommands", true),
                config.getBoolean("admin.regeneratePermissionsFileOnReload", false)
        );

        MainConfig.Ui ui = new MainConfig.Ui(
                readSortMode(config.getString("ui.defaultSortMode", "NEWEST_FIRST")),
                config.getBoolean("ui.showEmptyTopLevelCategories", true),
                config.getBoolean("ui.showListingCountsInCategoryLore", true),
                config.getBoolean("ui.interceptAllInventoryClicks", true)
        );

        return new MainConfig(storage, customItems, customItemIdentity, listingPolicies, claims, packagesConfig, market, search, admin, ui);
    }

    private List<MainConfig.Rule> readCustomIdentityRules(FileConfiguration config) {
        List<MainConfig.Rule> rules = new ArrayList<>();
        List<?> rawList = config.getList("customItemIdentity.rules");
        if (rawList == null) {
            return rules;
        }

        for (Object rawEntry : rawList) {
            if (!(rawEntry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            rules.add(new MainConfig.Rule(
                    stringValue(map.get("id"), "rule_" + (rules.size() + 1)),
                    stringValue(map.get("source"), "any_key"),
                    stringValue(map.get("section"), ""),
                    stringValue(map.get("key"), ""),
                    stringValue(map.get("resultMode"), "RAW_VALUE"),
                    stringValue(map.get("prefix"), ""),
                    booleanValue(map.get("appendMaterial"), false),
                    booleanValue(map.get("appendCustomModelData"), false)
            ));
        }
        return rules;
    }

    private List<MainConfig.ListingTier> readListingTiers(FileConfiguration config) {
        List<MainConfig.ListingTier> tiers = new ArrayList<>();
        List<?> rawList = config.getList("listingPolicies.tiers");
        if (rawList == null) {
            return tiers;
        }

        for (Object rawEntry : rawList) {
            if (!(rawEntry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            tiers.add(new MainConfig.ListingTier(
                    stringValue(map.get("permission"), ""),
                    positiveInt(map.get("maxListings"), 15),
                    positiveInt(map.get("listingDurationDays"), 7)
            ));
        }
        return tiers;
    }

    private SortMode readSortMode(String value) {
        String normalized = value == null ? "NEWEST_FIRST" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return SortMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return SortMode.NEWEST_FIRST;
        }
    }

    private String stringValue(Object rawValue, String fallback) {
        return rawValue == null ? fallback : String.valueOf(rawValue);
    }

    private boolean booleanValue(Object rawValue, boolean fallback) {
        if (rawValue instanceof Boolean bool) return bool;
        if (rawValue instanceof String text) return Boolean.parseBoolean(text);
        return fallback;
    }

    private int positiveInt(FileConfiguration config, String path, int fallback) { return Math.max(1, config.getInt(path, fallback)); }
    private int positiveInt(Object rawValue, int fallback) { return rawValue instanceof Number n ? Math.max(1, n.intValue()) : fallback; }
    private double nonNegativeDouble(FileConfiguration config, String path, double fallback) { return Math.max(0.0, config.getDouble(path, fallback)); }
}
