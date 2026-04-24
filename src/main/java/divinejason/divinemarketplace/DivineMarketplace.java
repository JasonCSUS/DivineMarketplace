package divinejason.divinemarketplace;

import org.bukkit.plugin.java.JavaPlugin;

import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.config.MainConfig;
import divinejason.divinemarketplace.config.MainConfigLoader;
import divinejason.divinemarketplace.setup.PluginFileInitializer;

/**
 * Main plugin entry point.
 *
 * TODO:
 * - Run PluginFileInitializer first.
 * - Load and validate MainConfig via MainConfigLoader.
 * - Store the loaded MainConfig in ConfigService singleton.
 * - Construct repositories/services/menu layer.
 * - Register commands and listeners.
 * - Start listing expiration task.
 * - Start analytics refresh task if needed.
 */
public final class DivineMarketplace extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("DivineMarketplace enabling...");

        // TODO: run file/bootstrap initializer
        new PluginFileInitializer(this).initialize();

                // TODO: load and store MainConfig
        MainConfig mainConfig = new MainConfigLoader().load(this);
        ConfigService.get().setMainConfig(mainConfig);
        // TODO: construct repositories
        // TODO: construct services
        // TODO: construct menu controllers
        // TODO: register listeners
        // TODO: register commands
        // TODO: schedule expiration / mailbox cleanup tasks

        getLogger().info("DivineMarketplace enabled.");
    }

    @Override
    public void onDisable() {
        // TODO: flush pending caches / save state / shutdown async tasks safely
        getLogger().info("DivineMarketplace disabled.");
    }
}
