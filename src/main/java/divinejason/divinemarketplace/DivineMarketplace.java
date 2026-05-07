package divinejason.divinemarketplace;

/*
 * Layer : plugin lifecycle
 * Owns  : Paper onEnable / onDisable, command registration, minimal plugin-level accessors
 * Calls : MarketRuntime (enable/reload/disable), MarketScheduler (task registration)
 *
 * This class is intentionally small.  All service construction and wiring
 * belongs in MarketRuntime.  All scheduler task registration belongs in
 * MarketScheduler.  This class exists only because Paper requires a JavaPlugin
 * subclass as the plugin entry point.
 */

import divinejason.divinemarketplace.bootstrap.MarketReloadScope;
import divinejason.divinemarketplace.bootstrap.MarketRuntime;
import divinejason.divinemarketplace.bootstrap.MarketScheduler;
import divinejason.divinemarketplace.command.MarketCommand;
import divinejason.divinemarketplace.command.MarketCommandFactory;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.config.MainConfig;
import divinejason.divinemarketplace.config.MainConfigLoader;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import divinejason.divinemarketplace.setup.PluginFileInitializer;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin entry point.
 *
 * <p>Responsibilities (only these):
 * <ul>
 *   <li>Bootstrap files and config on enable.
 *   <li>Locate the Vault Economy provider.
 *   <li>Delegate service construction to {@link MarketRuntime}.
 *   <li>Delegate task scheduling to {@link MarketScheduler}.
 *   <li>Register the /market command.
 *   <li>Ask StorageMaintenanceService for the startup retention pass.
 *   <li>Expose the tiny set of plugin-level accessors that Paper / other plugins need.
 * </ul>
 *
 */
public final class DivineMarketplace extends JavaPlugin {

    private PluginFileInitializer fileInitializer;
    private MainConfigLoader mainConfigLoader;
    private MarketRuntime runtime;

    // -------------------------------------------------------------------------
    // Paper lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        Logger log = getLogger();
        log.info("DivineMarketplace enabling…");
        try {
            bootstrapFiles();
            loadConfig();
            Economy economy = requireEconomy();

            runtime = new MarketRuntime(this, economy);
            runtime.enable();  // sync: schema init, service wiring — no SQL reads

            // Register the command while state is still LOADING_STORAGE.
            // MarketCommand.execute() guards with isReady() so players get the
            // "still loading" message until the async bootstrap completes.
            registerCommand("market", buildMarketCommand());

            // Scheduler guards skip tasks until READY; safe to register now.
            new MarketScheduler(this, runtime).registerAll();

            logStartupSummary(log);
            log.info("DivineMarketplace enabled. Loading storage async…");

            // Async: reads all SQL data → sets READY → runs retention pass.
            runtime.startAsyncLoad();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to enable DivineMarketplace.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) runtime.disable();
        getLogger().info("DivineMarketplace disabled.");
    }

    // -------------------------------------------------------------------------
    // Reload (called by /market reload command)
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> reloadRuntimeDataAsync(Set<MarketReloadScope> scopes) {
        if (runtime == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Market runtime is not available."));
        }
        return runtime.reloadAsync(scopes, () -> {
            if (scopes != null && scopes.contains(MarketReloadScope.CONFIG)) {
                bootstrapFiles();
                loadConfig();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private setup helpers
    // -------------------------------------------------------------------------

    private void bootstrapFiles() {
        fileInitializer = new PluginFileInitializer(this);
        fileInitializer.initialize();
    }

    private void loadConfig() {
        mainConfigLoader = new MainConfigLoader();
        MainConfig config = mainConfigLoader.load(this);
        ConfigService.get().setMainConfig(config);
    }

    private Economy requireEconomy() {
        RegisteredServiceProvider<Economy> provider =
            getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider not found.");
        }
        return provider.getProvider();
    }

    private MarketCommand buildMarketCommand() {
        return new MarketCommandFactory().create(this, runtime);
    }

    private void registerCommand(String name, MarketCommand handler) {
        registerCommand(name, "Open and manage the DivineMarketplace UI", handler);
    }

    private void logStartupSummary(Logger log) {
        Path data = getDataFolder().toPath();
        log.info(() -> "Data folder:      " + data.toAbsolutePath());
        log.info(() -> "market.db:        " + data.resolve(ConfigService.get().sqliteFile()).toAbsolutePath());
        log.info(() -> "SQLite size:      " + runtime.getSqliteStore().databaseStorageSizeBytes() + " B");
        log.info(() -> "Default duration: " + ConfigService.get().getMainConfig().listingPolicies().defaults().listingDurationDays() + " day(s)");
        log.info(() -> "Max listings:     " + ConfigService.get().getMainConfig().listingPolicies().defaults().maxListings());
        log.info(() -> "Vault provider:   " + runtime.getEconomy().getClass().getName());
    }

    // -------------------------------------------------------------------------
    // Plugin-level accessors (used by other plugins or Paper internals only)
    // -------------------------------------------------------------------------

    public MarketRuntime getRuntime() { return runtime; }
}
