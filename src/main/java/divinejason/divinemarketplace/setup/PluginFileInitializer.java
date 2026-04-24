package divinejason.divinemarketplace.setup;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Creates the plugin folder structure and any missing default files on startup.
 *
 * This class is the single owner of default file generation / bootstrap behavior.
 * Other config or registry classes should assume files already exist and only read them.
 *
 * Default editable files to create if missing:
 * - config.yml
 * - category_config.yml
 * - categories/<category>.yml
 * - custom/custom_items.yml
 * - custom/custom_enchants.yml
 * - permissions.txt
 *
 * Runtime binary state files to create if missing:
 * - data/listings.bin
 * - data/claims.bin
 * - data/sales.bin
 * - data/market_profiles.bin
 * - data/package_cache.bin
 * - data/unknown_custom_items.bin
 * - data/unknown_custom_enchants.bin
 * - data/admin_history.bin
 *
 * Intended generated file ownership:
 * - config.yml: global plugin behavior/settings only
 * - category_config.yml: top-level categories defined in config order
 * - categories/<category>.yml: vanilla/default item mappings for that category only
 * - custom/custom_items.yml: custom item definitions
 * - custom/custom_enchants.yml: custom enchant definitions / browse metadata
 * - permissions.txt: human-readable permission reference for LuckPerms/admins
 *
 * Current locked config truth:
 * - category icons support only:
 *   - "MATERIAL"
 *   - "MATERIAL:FLOAT_MODEL_DATA"
 * - do not use plugin-specific item identifiers in category icon config
 * - blank generated category files should use:
 *   categoryId: <id>
 *   items:
 *
 * Non-responsibilities:
 * - does not parse config into runtime objects
 * - does not build registries/services
 * - does not overwrite existing runtime state on normal startup
 */
public final class PluginFileInitializer {

    private final JavaPlugin plugin;

    public PluginFileInitializer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * TODO bootstrap flow:
     *
     * First boot:
     * 1. Ensure root plugin data folder exists.
     * 2. Ensure subfolders exist: categories/, custom/, logs/, data/.
     * 3. Generate config.yml from hard-coded/default resource if missing.
     * 4. Generate category_config.yml from hard-coded/default resource if missing.
     * 5. Read category_config.yml and collect configured category ids in config order.
     * 6. For each configured category id:
     *    - if categories/<categoryId>.yml exists, leave it alone
     *    - if missing and categoryId is a built-in default category, generate built-in default mapping file
     *    - if missing and categoryId is not a built-in default category, generate a blank category file
     * 7. Generate custom/custom_items.yml if missing.
     * 8. Generate custom/custom_enchants.yml if missing.
     * 9. Generate permissions.txt if missing.
     * 10. Create missing binary runtime state files if missing.
     * 11. Never wipe or overwrite live binary state on ordinary startup.
     *
     * Future boots:
     * 1. Ensure config.yml and category_config.yml still exist; regenerate defaults only if missing.
     * 2. Read category_config.yml.
     * 3. Ensure a categories/<categoryId>.yml file exists for every configured category.
     * 4. Missing built-in categories get built-in default mapping files.
     * 5. Missing non-built-in categories get blank mapping files.
     * 6. Existing files must never be overwritten automatically.
     *
     * TODO command/bootstrap interaction:
     * - reload should re-read editable YAML/config files and rebuild caches
     * - in-game admin define/sort commands should be able to write through to the
     *   backing YAML/CSV/DB immediately without needing a full restart
     */
    public void initialize() {
        // TODO implement folder/file bootstrap here
        plugin.getLogger().info("PluginFileInitializer placeholder: initialize default files/folders here.");
    }
}
