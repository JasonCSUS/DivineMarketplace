package divinejason.divinemarketplace.setup;

import java.nio.file.Path;
import java.util.List;

/**
 * Central path blueprint for the on-disk plugin layout.
 *
 * The bundled resources are copied into this live plugin folder on first run.
 * After that, loaders should read only the live plugin-folder files.
 */
public final class PluginDirectoryLayout {
    private PluginDirectoryLayout() {
    }

    public static final String CONFIG_YML = "config.yml";
    public static final String CATEGORY_CONFIG_YML = "category_config.yml";
    public static final String PERMISSIONS_TXT = "permissions.txt";

    public static final String DEFAULTS_CATEGORY_RESOURCE_PREFIX = "defaults/categories/";
    public static final String DEFAULTS_CUSTOM_RESOURCE_PREFIX = "defaults/custom/";

    public static final String CATEGORIES_DIR = "categories";
    public static final String CUSTOM_DIR = "custom";
    public static final String LOGS_DIR = "logs";
    public static final String DATA_DIR = "data";

    public static final String CUSTOM_ITEMS_YML = "custom/custom_items.yml";
    public static final String CUSTOM_ENCHANTS_YML = "custom/custom_enchants.yml";

    public static final String LOG_ADMIN_TRANSACTIONS = "logs/admin_transactions.log";
    public static final String LOG_UNKNOWN_CUSTOM_ITEMS = "logs/unknown_custom_items.log";
    public static final String LOG_UNKNOWN_CUSTOM_ENCHANTS = "logs/unknown_custom_enchants.log";
    public static final String LOG_EXPORTS_DIR = "logs/exports";

    public static final String DATA_LISTINGS = "data/listings.bin";
    public static final String DATA_ITEM_CLAIMS_DIR = "data/item_claims";
    public static final String DATA_MONEY_CLAIMS = "data/money_claims.bin";
    public static final String DATA_SALES = "data/sales.bin";
    public static final String DATA_MARKET_PROFILES = "data/market_profiles.bin";
    public static final String DATA_PACKAGE_CACHE = "data/package_cache.bin";
    public static final String DATA_UNKNOWN_CUSTOM_ITEMS = "data/unknown_custom_items.bin";
    public static final String DATA_UNKNOWN_CUSTOM_ENCHANTS = "data/unknown_custom_enchants.bin";
    public static final String DATA_ADMIN_SALES = "data/admin_sales.bin";
    public static final String DATA_ADMIN_LISTINGS = "data/admin_listings.bin";
    public static final String DATA_ADMIN_CLAIMS = "data/admin_claims.bin";

    public static Path resolveConfigFile(Path dataFolder) {
        return dataFolder.resolve(CONFIG_YML);
    }

    public static Path resolveCategoryConfigFile(Path dataFolder) {
        return dataFolder.resolve(CATEGORY_CONFIG_YML);
    }

    public static Path resolvePermissionsFile(Path dataFolder) {
        return dataFolder.resolve(PERMISSIONS_TXT);
    }

    public static Path resolveCustomItemsFile(Path dataFolder) {
        return dataFolder.resolve(CUSTOM_ITEMS_YML);
    }

    public static Path resolveCustomEnchantsFile(Path dataFolder) {
        return dataFolder.resolve(CUSTOM_ENCHANTS_YML);
    }

    public static Path resolveCategoryFile(Path dataFolder, String categoryId) {
        return dataFolder.resolve(CATEGORIES_DIR).resolve(categoryId + ".yml");
    }

    public static String bundledCategoryResource(String categoryId) {
        return DEFAULTS_CATEGORY_RESOURCE_PREFIX + categoryId + ".yml";
    }

    public static Path resolveItemClaimsDirectory(Path dataFolder) {
        return dataFolder.resolve(DATA_ITEM_CLAIMS_DIR);
    }

    public static Path resolveItemClaimShardFile(Path dataFolder, int shardIndex) {
        return resolveItemClaimsDirectory(dataFolder).resolve(String.format("shard_%02x.bin", shardIndex));
    }

    public static Path resolveItemClaimShardMetaFile(Path dataFolder, int shardIndex) {
        return resolveItemClaimsDirectory(dataFolder).resolve(String.format("shard_%02x.meta", shardIndex));
    }

    public static List<Path> requiredDirectories(Path dataFolder) {
        return List.of(
                dataFolder,
                dataFolder.resolve(CATEGORIES_DIR),
                dataFolder.resolve(CUSTOM_DIR),
                dataFolder.resolve(LOGS_DIR),
                dataFolder.resolve(LOG_EXPORTS_DIR),
                dataFolder.resolve(DATA_DIR),
                resolveItemClaimsDirectory(dataFolder)
        );
    }

    public static List<Path> requiredBinaryStateFiles(Path dataFolder) {
        return List.of(
                dataFolder.resolve(DATA_LISTINGS),
                dataFolder.resolve(DATA_MONEY_CLAIMS),
                dataFolder.resolve(DATA_SALES),
                dataFolder.resolve(DATA_MARKET_PROFILES),
                dataFolder.resolve(DATA_PACKAGE_CACHE),
                dataFolder.resolve(DATA_UNKNOWN_CUSTOM_ITEMS),
                dataFolder.resolve(DATA_UNKNOWN_CUSTOM_ENCHANTS),
                dataFolder.resolve(DATA_ADMIN_SALES),
                dataFolder.resolve(DATA_ADMIN_LISTINGS),
                dataFolder.resolve(DATA_ADMIN_CLAIMS)
        );
    }

    public static List<Path> requiredTextLogFiles(Path dataFolder) {
        return List.of(
                dataFolder.resolve(LOG_ADMIN_TRANSACTIONS),
                dataFolder.resolve(LOG_UNKNOWN_CUSTOM_ITEMS),
                dataFolder.resolve(LOG_UNKNOWN_CUSTOM_ENCHANTS)
        );
    }
}
