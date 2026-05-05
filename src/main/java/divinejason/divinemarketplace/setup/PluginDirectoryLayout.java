package divinejason.divinemarketplace.setup;

import java.nio.file.Path;
import java.util.List;

/**
 * Central path blueprint for the on-disk plugin layout.
 *
 * Current storage direction:
 * - core runtime market state lives in SQLite under data/market.db
 * - bundled defaults and editable custom definitions remain as text files
 * - logs/exports remain plain text/YAML for admin review
 */
public final class PluginDirectoryLayout {
    private PluginDirectoryLayout() {
    }

    public static final String CONFIG_YML = "config.yml";
    public static final String CATEGORY_CONFIG_YML = "category_config.yml";
    public static final String MENU_YML = "menu.yml";
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

    public static final String DATA_MARKET_DB = "data/market.db";

    public static Path resolveConfigFile(Path dataFolder) {
        return dataFolder.resolve(CONFIG_YML);
    }

    public static Path resolveCategoryConfigFile(Path dataFolder) {
        return dataFolder.resolve(CATEGORY_CONFIG_YML);
    }

    public static Path resolvePermissionsFile(Path dataFolder) {
        return dataFolder.resolve(PERMISSIONS_TXT);
    }

    public static Path resolveMenuConfigFile(Path dataFolder) {
        return dataFolder.resolve(MENU_YML);
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

    public static Path resolveMarketDatabaseFile(Path dataFolder) {
        return dataFolder.resolve(DATA_MARKET_DB);
    }

    public static List<Path> requiredDirectories(Path dataFolder) {
        return List.of(
                dataFolder,
                dataFolder.resolve(CATEGORIES_DIR),
                dataFolder.resolve(CUSTOM_DIR),
                dataFolder.resolve(LOGS_DIR),
                dataFolder.resolve(LOG_EXPORTS_DIR),
                dataFolder.resolve(DATA_DIR)
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
