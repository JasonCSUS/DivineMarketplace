package divinejason.divinemarketplace.config;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads and validates config.yml into MainConfig.
 *
 * Responsibilities:
 * - read config.yml after PluginFileInitializer has ensured it exists
 * - parse global plugin behavior/settings only
 * - validate ranges/defaults where needed
 * - build a MainConfig instance
 *
 * Non-responsibilities:
 * - does not generate config files
 * - does not read category_config.yml
 * - does not read categories/*.yml
 * - does not read custom/custom_items.yml
 * - does not read custom/custom_enchants.yml
 *
 * Parse focus includes:
 * - listing policy defaults/tiers
 * - market thresholds
 * - package preview mode
 * - storage backends
 * - binary storage size limits and cleanup thresholds
 * - icon/parser normalization rules that belong to config.yml itself
 */
public final class MainConfigLoader {

    public MainConfig load(JavaPlugin plugin) {
        // TODO parse config.yml into a typed MainConfig instance.
        return new MainConfig();
    }
}
