package divinejason.divinemarketplace.setup;

/**
 * Planning placeholder for the on-disk plugin layout.
 *
 * Intended layout:
 * plugins/DivineMarketplace/
 *   config.yml
 *   category_config.yml
 *   permissions.txt
 *
 *   categories/
 *     <category>.yml
 *
 *   custom/
 *     custom_items.yml
 *     custom_enchants.yml
 *
 *   logs/
 *     admin_transactions.log
 *     unknown_custom_items.log
 *     unknown_custom_enchants.log
 *
 *   data/
 *     listings.bin
 *     claims.bin
 *     sales.bin
 *     market_profiles.bin
 *     package_cache.bin
 *     unknown_custom_items.bin
 *     unknown_custom_enchants.bin
 */
public final class PluginDirectoryLayout {
    private PluginDirectoryLayout() {
    }
}
