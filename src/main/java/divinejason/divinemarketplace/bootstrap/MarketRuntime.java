package divinejason.divinemarketplace.bootstrap;

/*
 * Layer : bootstrap
 * Owns  : dependency construction, service wiring, and plugin runtime lifecycle
 * Calls : store, registry, service, menu runtime, and listener wiring — but NOT command registration
 *
 * This class is the single place where all stores, registries, and services are
 * constructed and wired together.  DivineMarketplace.java delegates enable/disable
 * to this class and exposes only the minimal plugin-level accessors that Paper
 * requires.  Menu/listener wiring lives here because those listeners need the
 * same runtime services; /market command construction lives in MarketCommandFactory.
 *
 * Scheduler tasks live in MarketScheduler, not here.
 */

import divinejason.divinemarketplace.auction.registry.custom.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemDataSource;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemRegistry;
import divinejason.divinemarketplace.auction.registry.custom.DefaultCustomItemRegistry;
import divinejason.divinemarketplace.auction.registry.custom.SQLiteCustomItemDataSource;
import divinejason.divinemarketplace.auction.service.admin.DefaultAdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.admin.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.category.CategoryResolver;
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.category.DefaultCategoryResolver;
import divinejason.divinemarketplace.auction.service.category.DefaultCategoryService;
import divinejason.divinemarketplace.auction.service.category.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.claim.ClaimService;
import divinejason.divinemarketplace.auction.service.claim.DefaultClaimService;
import divinejason.divinemarketplace.auction.service.claim.StorageItemDeliveryHelper;
import divinejason.divinemarketplace.auction.service.enchant.DefaultEnchantmentMetadataService;
import divinejason.divinemarketplace.auction.service.event.DefaultMarketEventService;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.service.history.DefaultHistoryService;
import divinejason.divinemarketplace.auction.service.history.HistoryService;
import divinejason.divinemarketplace.auction.service.identity.ConfigDrivenCustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.identity.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.identity.DefaultItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.identity.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.identity.ListingPolicyResolver;
import divinejason.divinemarketplace.auction.service.identity.StoredEnchantExtractor;
import divinejason.divinemarketplace.auction.service.listing.DefaultListingService;
import divinejason.divinemarketplace.auction.service.listing.ListingService;
import divinejason.divinemarketplace.auction.service.listing.ListingWriteHelper;
import divinejason.divinemarketplace.auction.service.pricing.DefaultPriceRecommendationService;
import divinejason.divinemarketplace.auction.service.pricing.MarketProfileCalculator;
import divinejason.divinemarketplace.auction.service.pricing.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.pricing.PriceRecommendationService;
import divinejason.divinemarketplace.auction.service.purchase.DefaultPurchaseService;
import divinejason.divinemarketplace.auction.service.purchase.PurchaseService;
import divinejason.divinemarketplace.auction.service.storage.StorageMaintenanceService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketEventStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketIndexStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketPriceStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteRecommendationHistoryStore;
import divinejason.divinemarketplace.concurrency.MarketActionGate;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.menu.*;
import divinejason.divinemarketplace.util.PerfTimer;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import divinejason.divinemarketplace.setup.MarketRuntimeStateStore;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import divinejason.divinemarketplace.storage.sqlite.SQLiteDatabase;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wires all stores, registries, and services together.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Construct via {@link #MarketRuntime(JavaPlugin, Economy)}.
 *   <li>Call {@link #enable()} once during plugin enable.
 *   <li>Call {@link #reload()} on /market reload.
 *   <li>Call {@link #disable()} on plugin disable.
 * </ol>
 *
 * <p>Invariant: all public getters return non-null after {@link #enable()} returns.
 */
public final class MarketRuntime {

    private static final int STORAGE_RECOVERY_MAX_ATTEMPTS = 3;
    private static final long TICKS_PER_SECOND = 20L;

    private final JavaPlugin plugin;
    private final Economy economy;
    private final Logger logger;
    private final AtomicReference<MarketRuntimeState> state =
        new AtomicReference<>(MarketRuntimeState.STARTING);
    private final AtomicBoolean storageRecoveryInProgress = new AtomicBoolean(false);

    // -- storage --
    private SQLiteDatabase sqliteDatabase;
    private SQLiteStore sqliteStore;
    private SQLiteWriteBehindQueue writeBehindQueue;

    // -- active state stores (canonical write targets) --
    private SQLiteListingStore listingStore;
    private SQLiteItemClaimStore itemClaimStore;
    private SQLiteMoneyClaimStore moneyClaimStore;
    private SQLiteMarketIndexStore marketIndexStore;
    private SQLiteMarketPriceStore marketPriceStore;
    private SQLiteRecommendationHistoryStore recommendationHistoryStore;
    private SQLiteCustomEnchantStore customEnchantStore;
    private SQLiteCustomItemOverrideStore customItemOverrideStore;

    // -- canonical event store --
    private SQLiteMarketEventStore marketEventStore;

    // -- registries --
    private CustomItemDataSource customItemDataSource;
    private CustomItemRegistry customItemRegistry;
    private CustomItemTypeExtractor customItemTypeExtractor;
    private CustomItemMetadataLogService customItemMetadataLogService;
    private CustomItemCollisionLogService customItemCollisionLogService;
    private DefaultEnchantmentMetadataService enchantmentMetadataService;
    private StoredEnchantExtractor storedEnchantExtractor;
    private CategoryResolver categoryResolver;
    private ItemIdentityResolver itemIdentityResolver;
    private FlattenedMarketIndexService flattenedMarketIndexService;
    private MarketRuntimeStateStore marketRuntimeStateStore;

    // -- services --
    private MarketEventService marketEventService;
    private PriceRecommendationService priceRecommendationService;
    private CategoryService categoryService;
    private DefaultAdminHistoryService adminHistoryService;
    private DefaultAdminHistoryExportService adminHistoryExportService;
    private HistoryService historyService;
    private ListingPolicyResolver listingPolicyResolver;
    private StorageItemDeliveryHelper storageItemDeliveryHelper;
    private ListingWriteHelper listingWriteHelper;
    private ListingService listingService;
    private ClaimService claimService;
    private PurchaseService purchaseService;
    private MarketRecalculationService marketRecalculationService;
    private StorageMaintenanceService storageMaintenanceService;

    // -- GUI runtime --
    private MenuSessionManager menuSessionManager;
    private MenuDataFacade menuDataFacade;
    private MenuController menuController;
    private MenuDataVersion menuDataVersion;
    private MenuPageCache menuPageCache;
    private PageModelBuilder pageModelBuilder;
    private MenuPagePreparationService menuPagePreparationService;
    private MenuInvalidationService menuInvalidationService;
    private MarketChatPromptService chatPromptService;
    private MarketActionGate actionGate;

    public MarketRuntime(JavaPlugin plugin, Economy economy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.logger = plugin.getLogger();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Sync phase: opens the database, creates tables, wires all services and GUI.
     * No SQL data is read here. State stays LOADING_STORAGE after this returns.
     * Call {@link #startAsyncLoad()} next to hydrate stores and transition to READY.
     */
    public void enable() {
        state.set(MarketRuntimeState.LOADING_STORAGE);
        try {
            openDatabase();
            buildStores();
            buildRegistries();
            buildServices();
            buildMenuRuntime();
        } catch (RuntimeException | Error e) {
            state.set(MarketRuntimeState.FAILED);
            throw e;
        }
    }

    /**
     * Async phase: loads all SQL data into memory on a background thread, then
     * transitions to READY on the main thread and schedules the startup retention pass.
     * Must be called after {@link #enable()} and after command registration.
     */
    public void startAsyncLoad() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                loadAllFromStorage();
            } catch (Exception e) {
                logger.severe("[DivineMarketplace] Async storage load failed: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    state.set(MarketRuntimeState.FAILED));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                state.set(MarketRuntimeState.READY);
                logger.info("[DivineMarketplace] Storage loaded — marketplace is open.");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> storageMaintenanceService.runRetentionPass());
            });
        });
    }

    /** Loads all stores and services from SQLite in dependency order. Runs on an async thread. */
    private void loadAllFromStorage() {
        boolean perf = PerfTimer.enabled();
        long startNanos = perf ? System.nanoTime() : 0;

        // Raw stores — SQL reads, no ordering dependency between them
        listingStore.loadFromStorage();
        itemClaimStore.loadFromStorage();
        moneyClaimStore.loadFromStorage();
        marketEventService.loadFromStorage();   // canonical event store + rebuilds index
        recommendationHistoryStore.loadFromStorage();
        customItemOverrideStore.loadFromStorage();
        // Market index and derived registries — depend on SQL tables but not on store caches
        flattenedMarketIndexService.loadFromStorage();
        customItemRegistry.reload();          // interface — delegates to loadFromStorage() internally
        enchantmentMetadataService.loadFromStorage();
        // Pure in-memory view rebuilds — delegate to flattenedMarketIndexService / listingStore
        if (categoryResolver instanceof DefaultCategoryResolver r) r.reload();
        if (categoryService instanceof DefaultCategoryService s) s.reload();
        priceRecommendationService.reload();  // interface — delegates to loadFromStorage() internally

        if (perf) {
            logger.info("[DivineMarketplace][perf] startup storage load"
                    + " listings=" + listingStore.countAll()
                    + " claims=" + itemClaimStore.countAll()
                    + " events=" + marketEventService.countAll()
                    + " time=" + (System.nanoTime() - startNanos) / 1_000_000 + "ms");
        }
    }

    /**
     * Legacy full reload is intentionally disabled. Use {@link #reloadAsync(Set, Runnable)}
     * with explicit scopes so admin commands cannot accidentally reload every SQL-backed
     * runtime store on the main server thread.
     */
    @Deprecated(forRemoval = true)
    public void reload() {
        throw new UnsupportedOperationException("Use targeted async reload scopes instead of full runtime reload.");
    }

    /**
     * Runs targeted reload work off-thread where possible, then commits any Bukkit-facing
     * menu renderer change on the main thread.  This path keeps WAL/database state intact
     * and flushes queued writes before price data is re-read.
     */
    public CompletableFuture<Void> reloadAsync(Set<MarketReloadScope> requestedScopes, Runnable configReloadAction) {
        Objects.requireNonNull(configReloadAction, "configReloadAction");
        EnumSet<MarketReloadScope> scopes = requestedScopes == null || requestedScopes.isEmpty()
                ? EnumSet.of(MarketReloadScope.CONFIG)
                : EnumSet.copyOf(requestedScopes);

        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!state.compareAndSet(MarketRuntimeState.READY, MarketRuntimeState.LOADING_STORAGE)) {
            result.completeExceptionally(new IllegalStateException("Market runtime is not ready for reload: " + state.get()));
            return result;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (writeBehindQueue != null) {
                    writeBehindQueue.flushBlocking();
                }

                if (scopes.contains(MarketReloadScope.CONFIG)) {
                    configReloadAction.run();
                    flattenedMarketIndexService.loadFromStorage();
                    customItemRegistry.reload();
                    enchantmentMetadataService.loadFromStorage();
                    if (categoryResolver instanceof DefaultCategoryResolver r) r.reload();
                    if (categoryService instanceof DefaultCategoryService s) s.reload();
                }

                if (scopes.contains(MarketReloadScope.PRICES)) {
                    priceRecommendationService.reload();
                }
            } catch (Throwable throwable) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    state.set(MarketRuntimeState.READY);
                    result.completeExceptionally(throwable);
                });
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (scopes.contains(MarketReloadScope.CONFIG)) {
                        menuInvalidationService.markCategoriesChanged();
                    }
                    if (scopes.contains(MarketReloadScope.PRICES)) {
                        menuInvalidationService.markPricesChanged();
                    }
                    if (scopes.contains(MarketReloadScope.MENU) || scopes.contains(MarketReloadScope.CONFIG)) {
                        menuInvalidationService.markMenuConfigChanged();
                        menuController.updateRenderer(buildMenuRenderer());
                    }
                    state.set(MarketRuntimeState.READY);
                    result.complete(null);
                } catch (Throwable throwable) {
                    state.set(MarketRuntimeState.FAILED);
                    result.completeExceptionally(throwable);
                }
            });
        });
        return result;
    }

    /** Flushes pending writes and releases resources. Call once from onDisable. */
    public void disable() {
        state.set(MarketRuntimeState.DISABLING);
        if (writeBehindQueue != null) writeBehindQueue.shutdownAndFlush();
        if (sqliteDatabase != null) sqliteDatabase.close();
        if (actionGate != null) actionGate.clear();
        if (menuSessionManager != null) menuSessionManager.clearAll();
        if (chatPromptService  != null) chatPromptService.clearAll();
        ConfigService.get().clear();
    }

    // -------------------------------------------------------------------------
    // Private construction helpers
    // -------------------------------------------------------------------------

    private void openDatabase() {
        Path dbPath = plugin.getDataFolder().toPath().resolve(ConfigService.get().sqliteFile());
        sqliteDatabase = SQLiteDatabase.open("divinemarketplace", dbPath);
        sqliteStore = sqliteDatabase.store(ConfigService.get().sqliteModulePrefix());
        writeBehindQueue = new SQLiteWriteBehindQueue(sqliteStore, logger);
        writeBehindQueue.setFlushFailureHandler(this::handleWriteBehindFlushFailure);
    }

    private void buildStores() {
        listingStore              = new SQLiteListingStore(sqliteStore, writeBehindQueue, logger);
        itemClaimStore            = new SQLiteItemClaimStore(sqliteStore);
        moneyClaimStore           = new SQLiteMoneyClaimStore(sqliteStore);
        marketEventStore          = new SQLiteMarketEventStore(sqliteStore);  // canonical event store
        marketIndexStore          = new SQLiteMarketIndexStore(sqliteStore);
        marketPriceStore          = new SQLiteMarketPriceStore(sqliteStore);
        recommendationHistoryStore = new SQLiteRecommendationHistoryStore(sqliteStore);
        customEnchantStore        = new SQLiteCustomEnchantStore(sqliteStore);
        customItemOverrideStore   = new SQLiteCustomItemOverrideStore(sqliteStore, writeBehindQueue);
    }

    private void buildRegistries() {
        flattenedMarketIndexService    = new FlattenedMarketIndexService(plugin, marketIndexStore, writeBehindQueue);
        marketRuntimeStateStore        = new MarketRuntimeStateStore(sqliteStore);

        marketEventService             = new DefaultMarketEventService(marketEventStore);

        Path dataFolder = plugin.getDataFolder().toPath();
        customItemDataSource           = new SQLiteCustomItemDataSource(marketIndexStore, writeBehindQueue);
        customItemCollisionLogService  = new CustomItemCollisionLogService(dataFolder);
        customItemRegistry             = new DefaultCustomItemRegistry(logger, customItemDataSource, customItemCollisionLogService);
        customItemMetadataLogService   = new CustomItemMetadataLogService(dataFolder);
        customItemTypeExtractor        = new ConfigDrivenCustomItemTypeExtractor(customItemOverrideStore);
        enchantmentMetadataService     = new DefaultEnchantmentMetadataService(customEnchantStore, writeBehindQueue);
        storedEnchantExtractor         = new StoredEnchantExtractor();
        categoryResolver               = new DefaultCategoryResolver(flattenedMarketIndexService);
        itemIdentityResolver           = new DefaultItemIdentityResolver(
            categoryResolver, customItemRegistry, customItemTypeExtractor,
            customItemMetadataLogService, enchantmentMetadataService, storedEnchantExtractor);
    }

    private void buildServices() {
        priceRecommendationService = new DefaultPriceRecommendationService(
            plugin, flattenedMarketIndexService,
            new DefaultPriceRecommendationService.ActiveListingLookup() {
                @Override
                public List<divinejason.divinemarketplace.auction.model.Listing> getActiveListingsForMarketKey(String marketKey) {
                    return listingStore.findActiveByMarketKeyUnsorted(marketKey);
                }
                @Override
                public List<divinejason.divinemarketplace.auction.model.Listing> getAllActiveListings() {
                    return listingStore.findAllActiveUnsorted();
                }
            },
            marketEventService, new MarketProfileCalculator(), marketPriceStore, recommendationHistoryStore, writeBehindQueue);

        categoryService        = new DefaultCategoryService(() -> listingStore.findAllActiveUnsorted(), flattenedMarketIndexService, enchantmentMetadataService);

        adminHistoryService        = new DefaultAdminHistoryService(marketEventService);
        adminHistoryExportService  = new DefaultAdminHistoryExportService(
            adminHistoryService,
            plugin.getDataFolder().toPath().resolve("logs").resolve("exports"));

        historyService             = new DefaultHistoryService(marketEventService, recommendationHistoryStore);
        listingPolicyResolver      = new ListingPolicyResolver();
        storageItemDeliveryHelper  = new StorageItemDeliveryHelper();
        listingWriteHelper         = new ListingWriteHelper(listingStore, categoryService);

        listingService  = new DefaultListingService(listingStore, itemClaimStore, marketEventService, writeBehindQueue, itemIdentityResolver,
            categoryService, listingPolicyResolver, listingWriteHelper);
        claimService    = new DefaultClaimService(itemClaimStore, moneyClaimStore, marketEventService, writeBehindQueue, itemIdentityResolver,
            economy, storageItemDeliveryHelper, listingPolicyResolver, listingWriteHelper);
        purchaseService = new DefaultPurchaseService(listingStore, itemClaimStore, moneyClaimStore,
            marketEventService, writeBehindQueue,
            itemIdentityResolver, categoryService, storageItemDeliveryHelper, economy);

        marketRecalculationService = new MarketRecalculationService(
            plugin, flattenedMarketIndexService, priceRecommendationService, marketRuntimeStateStore, writeBehindQueue, this::isReady);
        storageMaintenanceService = new StorageMaintenanceService(
            logger, sqliteStore, writeBehindQueue, marketEventService, itemClaimStore);
    }

    private void buildMenuRuntime() {
        menuSessionManager = new MenuSessionManager();
        actionGate         = new MarketActionGate();
        menuDataVersion    = new MenuDataVersion();
        menuPageCache      = new MenuPageCache();
        menuDataFacade     = new MenuDataFacade(categoryService, listingStore, itemClaimStore, moneyClaimStore, historyService, listingPolicyResolver);
        pageModelBuilder   = new PageModelBuilder(menuDataFacade);
        menuPagePreparationService = new MenuPagePreparationService(plugin, menuDataVersion, menuPageCache, pageModelBuilder);
        menuInvalidationService = new MenuInvalidationService(menuDataVersion, menuPagePreparationService);
        menuController     = new MenuController(plugin, menuSessionManager, buildMenuRenderer(), menuDataVersion, menuPagePreparationService, menuInvalidationService);
        chatPromptService  = new MarketChatPromptService(plugin, listingService, claimService, menuController, menuDataFacade);
        MenuClickRouter clickRouter = new MenuClickRouter(menuSessionManager, menuController, menuDataFacade,
            listingService, claimService, purchaseService, chatPromptService, actionGate, this::isReady);
        plugin.getServer().getPluginManager().registerEvents(
            new divinejason.divinemarketplace.menu.MarketMenuListener(plugin, menuSessionManager, clickRouter), plugin);
        plugin.getServer().getPluginManager().registerEvents(chatPromptService, plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new divinejason.divinemarketplace.listener.PlayerConnectionListener(menuSessionManager, chatPromptService), plugin);
    }

    private MenuRenderer buildMenuRenderer() {
        MenuVisualConfig visual = new MenuVisualConfigLoader(logger)
            .load(PluginDirectoryLayout.resolveMenuConfigFile(plugin.getDataFolder().toPath()));
        return new MenuRenderer(menuDataFacade, new MenuItemFactory(visual), visual, menuDataVersion, menuPageCache, pageModelBuilder);
    }


    // -------------------------------------------------------------------------
    // Storage recovery
    // -------------------------------------------------------------------------

    /**
     * Called by the write-behind queue after an async flush fails and the failed
     * mutations have already been restored to the front of the queue.
     *
     * <p>Recovery keeps in-memory marketplace state intact. It does not reload
     * from SQL because SQL may be behind the accepted in-memory actions. Instead
     * it reconnects the SQLite/Hikari datasource and retries a blocking flush of
     * the same queued mutations. Entry points reject new work while the runtime is
     * RECOVERING_STORAGE.</p>
     */
    private void handleWriteBehindFlushFailure(Throwable failure) {
        if (state.get() == MarketRuntimeState.DISABLING || state.get() == MarketRuntimeState.FAILED) {
            return;
        }
        if (!storageRecoveryInProgress.compareAndSet(false, true)) {
            return;
        }

        MarketRuntimeState previous = state.getAndSet(MarketRuntimeState.RECOVERING_STORAGE);
        logger.severe("[DivineMarketplace] SQLite write-behind flush failed while runtime was "
            + previous + ". Marketplace actions are paused while storage recovery retries. Cause: "
            + failure.getMessage());
        scheduleStorageRecoveryAttempt(1);
    }

    private void scheduleStorageRecoveryAttempt(int attempt) {
        long delayTicks = storageRecoveryDelayTicks(attempt);
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
            () -> runStorageRecoveryAttempt(attempt), delayTicks);
    }

    private void runStorageRecoveryAttempt(int attempt) {
        if (state.get() != MarketRuntimeState.RECOVERING_STORAGE) {
            storageRecoveryInProgress.set(false);
            return;
        }

        try {
            logger.warning("[DivineMarketplace] Storage recovery attempt " + attempt
                + "/" + STORAGE_RECOVERY_MAX_ATTEMPTS + ": reconnecting SQLite and flushing queued writes.");
            if (sqliteDatabase != null) {
                sqliteDatabase.reconnect();
            }
            if (writeBehindQueue != null) {
                writeBehindQueue.flushBlocking();
            }
        } catch (Throwable throwable) {
            logger.severe("[DivineMarketplace] Storage recovery attempt " + attempt
                + " failed: " + throwable.getMessage());
            if (attempt < STORAGE_RECOVERY_MAX_ATTEMPTS && state.get() == MarketRuntimeState.RECOVERING_STORAGE) {
                scheduleStorageRecoveryAttempt(attempt + 1);
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    state.set(MarketRuntimeState.FAILED);
                    storageRecoveryInProgress.set(false);
                    logger.severe("[DivineMarketplace] Storage recovery failed. Marketplace runtime is now FAILED; "
                        + "restart the server/plugin after fixing the storage issue.");
                });
            }
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (state.get() == MarketRuntimeState.RECOVERING_STORAGE) {
                state.set(MarketRuntimeState.READY);
                logger.info("[DivineMarketplace] Storage recovery succeeded; marketplace is open again.");
            }
            storageRecoveryInProgress.set(false);
        });
    }

    private long storageRecoveryDelayTicks(int attempt) {
        return switch (Math.max(1, attempt)) {
            case 1 -> 2L * TICKS_PER_SECOND;
            case 2 -> 5L * TICKS_PER_SECOND;
            default -> 10L * TICKS_PER_SECOND;
        };
    }

    // -------------------------------------------------------------------------
    // Accessors used by DivineMarketplace and MarketScheduler
    // -------------------------------------------------------------------------

    public boolean isReady()              { return state.get() == MarketRuntimeState.READY; }
    public MarketRuntimeState getState()  { return state.get(); }

    public SQLiteStore getSqliteStore()                              { return sqliteStore; }
    public SQLiteWriteBehindQueue getWriteBehindQueue()              { return writeBehindQueue; }
    public SQLiteListingStore getListingStore()                      { return listingStore; }
    public SQLiteItemClaimStore getItemClaimStore()                  { return itemClaimStore; }
    public MarketEventService getMarketEventService()                { return marketEventService; }

    public ListingService getListingService()                        { return listingService; }
    public ClaimService getClaimService()                            { return claimService; }
    public PurchaseService getPurchaseService()                      { return purchaseService; }
    public CategoryService getCategoryService()                      { return categoryService; }
    public ItemIdentityResolver getItemIdentityResolver()            { return itemIdentityResolver; }
    public CategoryResolver getCategoryResolver()                    { return categoryResolver; }
    public FlattenedMarketIndexService getFlattenedMarketIndexService() { return flattenedMarketIndexService; }
    public CustomItemRegistry getCustomItemRegistry()                { return customItemRegistry; }
    public PriceRecommendationService getPriceRecommendationService() { return priceRecommendationService; }
    public MarketRecalculationService getMarketRecalculationService() { return marketRecalculationService; }
    public StorageMaintenanceService getStorageMaintenanceService()  { return storageMaintenanceService; }
    public HistoryService getHistoryService()                        { return historyService; }
    public DefaultAdminHistoryService getAdminHistoryService()       { return adminHistoryService; }
    public DefaultAdminHistoryExportService getAdminHistoryExportService() { return adminHistoryExportService; }
    public CustomItemCollisionLogService getCustomItemCollisionLogService() { return customItemCollisionLogService; }
    public CustomItemTypeExtractor getCustomItemTypeExtractor()      { return customItemTypeExtractor; }
    public CustomItemMetadataLogService getCustomItemMetadataLogService() { return customItemMetadataLogService; }
    public SQLiteCustomItemOverrideStore getCustomItemOverrideStore() { return customItemOverrideStore; }
    public StoredEnchantExtractor getStoredEnchantExtractor()        { return storedEnchantExtractor; }
    public SQLiteCustomEnchantStore getCustomEnchantStore()          { return customEnchantStore; }
    public DefaultEnchantmentMetadataService getEnchantmentMetadataService() { return enchantmentMetadataService; }
    public MenuController getMenuController()                        { return menuController; }
    public MenuInvalidationService getMenuInvalidationService()      { return menuInvalidationService; }
    public MarketChatPromptService getChatPromptService()            { return chatPromptService; }
    public Economy getEconomy()                                      { return economy; }
}
