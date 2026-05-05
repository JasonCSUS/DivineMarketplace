package divinejason.divinemarketplace;

import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteAdminClaimsStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteAdminListingsStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteAdminSalesStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMarketIndexStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMarketPriceStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecommendationHistoryStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteSalesStore;
import divinejason.divinemarketplace.auction.service.*;
import divinejason.divinemarketplace.command.MarketCommand;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.config.MainConfig;
import divinejason.divinemarketplace.config.MainConfigLoader;
import divinejason.divinemarketplace.listener.PlayerConnectionListener;
import divinejason.divinemarketplace.menu.*;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import divinejason.divinemarketplace.setup.MarketRuntimeStateStore;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import divinejason.divinemarketplace.setup.PluginFileInitializer;
import divinejason.divinemarketplace.storage.sqlite.SQLiteDatabase;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DivineMarketplace extends JavaPlugin {
    private PluginFileInitializer fileInitializer;
    private MainConfigLoader mainConfigLoader;
    private MainConfig mainConfig;

    private Economy economy;
    private SQLiteDatabase sqliteDatabase;
    private SQLiteStore sqliteStore;

    private SQLiteListingStore listingStore;
    private SQLiteItemClaimStore itemClaimStore;
    private SQLiteMoneyClaimStore moneyClaimStore;
    private SQLiteSalesStore salesStore;
    private SQLiteAdminSalesStore adminSalesStore;
    private SQLiteAdminListingsStore adminListingsStore;
    private SQLiteAdminClaimsStore adminClaimsStore;
    private SQLiteMarketIndexStore marketIndexStore;
    private SQLiteMarketPriceStore marketPriceStore;
    private SQLiteRecommendationHistoryStore recommendationHistoryStore;
    private SQLiteCustomEnchantStore customEnchantStore;
    private SQLiteCustomItemOverrideStore customItemOverrideStore;

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
    private InMemorySaleHistoryIndex saleHistoryIndex;
    private PriceRecommendationService priceRecommendationService;
    private MarketRecalculationService marketRecalculationService;
    private MarketRuntimeStateStore marketRuntimeStateStore;
    private HistoryService historyService;

    private CategoryService categoryService;
    private MarketAnalyticsService marketAnalyticsService;
    private AdminHistoryService adminHistoryService;
    private AdminHistoryExportService adminHistoryExportService;

    private ListingPolicyResolver listingPolicyResolver;
    private StorageItemDeliveryHelper storageItemDeliveryHelper;
    private ListingWriteHelper listingWriteHelper;

    private ListingService listingService;
    private ClaimService claimService;
    private PurchaseService purchaseService;

    private MarketCommand marketCommand;
    private MenuController menuController;
    private MenuSessionManager menuSessionManager;
    private MenuDataFacade menuDataFacade;
    private MarketChatPromptService chatPromptService;

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        logger.info("DivineMarketplace enabling...");
        try {
            bootstrapFiles();
            loadAndCacheMainConfig();
            setupEconomyOrThrow();
            openDatabase();
            initializeRuntimeObjects();
            registerCommand("market", marketCommand);
            marketRecalculationService.scheduleStartupAndDailyChecks();
            scheduleHourlyListingExpiration();
            scheduleListingWriteFlush();
            scheduleItemClaimSoftLimitCleanup();
            logStartupSummary(logger);
            logger.info("DivineMarketplace enabled.");
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to enable DivineMarketplace.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (listingStore != null) listingStore.flushPendingWritesBlocking();
            if (sqliteDatabase != null) sqliteDatabase.close();
        } catch (Exception exception) {
            getLogger().warning("Failed to close SQLite database cleanly: " + exception.getMessage());
        }
        if (menuSessionManager != null) menuSessionManager.clearAll();
        if (chatPromptService != null) chatPromptService.clearAll();
        ConfigService.get().clear();
        mainConfig = null;
        getLogger().info("DivineMarketplace disabled.");
    }

    private void bootstrapFiles() { fileInitializer = new PluginFileInitializer(this); fileInitializer.initialize(); }
    private void loadAndCacheMainConfig() { mainConfigLoader = new MainConfigLoader(); mainConfig = mainConfigLoader.load(this); ConfigService.get().setMainConfig(mainConfig); }

    private void setupEconomyOrThrow() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) throw new IllegalStateException("Vault economy provider was not found.");
        economy = provider.getProvider();
    }

    private void openDatabase() {
        Path dbPath = getDataFolder().toPath().resolve(ConfigService.get().sqliteFile());
        sqliteDatabase = SQLiteDatabase.open("divinemarketplace", dbPath);
        sqliteStore = sqliteDatabase.store(ConfigService.get().sqliteModulePrefix());
    }

    private void initializeRuntimeObjects() {
        listingStore = new SQLiteListingStore(sqliteStore, getLogger());
        itemClaimStore = new SQLiteItemClaimStore(sqliteStore);
        moneyClaimStore = new SQLiteMoneyClaimStore(sqliteStore);
        salesStore = new SQLiteSalesStore(sqliteStore);
        adminSalesStore = new SQLiteAdminSalesStore(sqliteStore);
        adminListingsStore = new SQLiteAdminListingsStore(sqliteStore);
        adminClaimsStore = new SQLiteAdminClaimsStore(sqliteStore);
        marketIndexStore = new SQLiteMarketIndexStore(sqliteStore);
        marketPriceStore = new SQLiteMarketPriceStore(sqliteStore);
        recommendationHistoryStore = new SQLiteRecommendationHistoryStore(sqliteStore);
        customEnchantStore = new SQLiteCustomEnchantStore(sqliteStore);
        customItemOverrideStore = new SQLiteCustomItemOverrideStore(sqliteStore);

        flattenedMarketIndexService = new FlattenedMarketIndexService(this, marketIndexStore);
        saleHistoryIndex = new InMemorySaleHistoryIndex(salesStore);
        marketRuntimeStateStore = new MarketRuntimeStateStore(sqliteStore);
        historyService = new DefaultHistoryService(saleHistoryIndex, recommendationHistoryStore);

        customItemDataSource = new SQLiteCustomItemDataSource(marketIndexStore);
        customItemCollisionLogService = new CustomItemCollisionLogService(getDataFolder().toPath());
        customItemRegistry = new DefaultCustomItemRegistry(getLogger(), customItemDataSource, customItemCollisionLogService);
        customItemMetadataLogService = new CustomItemMetadataLogService(getDataFolder().toPath());
        customItemTypeExtractor = new ConfigDrivenCustomItemTypeExtractor(customItemOverrideStore);
        enchantmentMetadataService = new DefaultEnchantmentMetadataService(customEnchantStore);
        storedEnchantExtractor = new StoredEnchantExtractor();
        categoryResolver = new DefaultCategoryResolver(flattenedMarketIndexService);
        itemIdentityResolver = new DefaultItemIdentityResolver(categoryResolver, customItemRegistry, customItemTypeExtractor, customItemMetadataLogService, enchantmentMetadataService, storedEnchantExtractor);

        priceRecommendationService = new DefaultPriceRecommendationService(
                this,
                flattenedMarketIndexService,
                new DefaultPriceRecommendationService.ActiveListingLookup() {
                    @Override
                    public java.util.List<divinejason.divinemarketplace.auction.model.Listing> getActiveListingsForMarketKey(String marketKey) {
                        return listingStore.findActiveByMarketKeyUnsorted(marketKey);
                    }

                    @Override
                    public java.util.List<divinejason.divinemarketplace.auction.model.Listing> getAllActiveListings() {
                        return listingStore.findAllActiveUnsorted();
                    }
                },
                saleHistoryIndex,
                new MarketProfileCalculator(),
                marketPriceStore,
                recommendationHistoryStore
        );

        categoryService = new DefaultCategoryService(() -> listingStore.findAllActiveUnsorted(), flattenedMarketIndexService, enchantmentMetadataService);
        marketAnalyticsService = new DefaultMarketAnalyticsService(salesStore, saleHistoryIndex);
        adminHistoryService = new DefaultAdminHistoryService(adminSalesStore, adminListingsStore, adminClaimsStore);
        adminHistoryExportService = new DefaultAdminHistoryExportService((DefaultAdminHistoryService) adminHistoryService, getDataFolder().toPath().resolve("logs").resolve("exports"));

        listingPolicyResolver = new ListingPolicyResolver();
        storageItemDeliveryHelper = new StorageItemDeliveryHelper();
        listingWriteHelper = new ListingWriteHelper(listingStore, categoryService, marketAnalyticsService);

        listingService = new DefaultListingService(listingStore, itemClaimStore, itemIdentityResolver, adminHistoryService, categoryService, marketAnalyticsService, listingPolicyResolver, listingWriteHelper);
        claimService = new DefaultClaimService(itemClaimStore, moneyClaimStore, itemIdentityResolver, adminHistoryService, economy, storageItemDeliveryHelper, listingPolicyResolver, listingWriteHelper);
        purchaseService = new DefaultPurchaseService(listingStore, itemClaimStore, moneyClaimStore, itemIdentityResolver, adminHistoryService, categoryService, marketAnalyticsService, storageItemDeliveryHelper, economy);

        initializeMenuRuntime();

        marketRecalculationService = new MarketRecalculationService(this, flattenedMarketIndexService, priceRecommendationService, marketRuntimeStateStore);
        marketCommand = new MarketCommand(this, menuController, chatPromptService, listingService, claimService, listingStore, historyService, (DefaultAdminHistoryService) adminHistoryService, adminHistoryExportService, categoryService, flattenedMarketIndexService, priceRecommendationService, itemIdentityResolver, customItemRegistry, marketRecalculationService, customEnchantStore, customItemTypeExtractor, customItemMetadataLogService, customItemOverrideStore, customItemCollisionLogService, storedEnchantExtractor);
    }

    private void initializeMenuRuntime() {
        menuSessionManager = new MenuSessionManager();
        menuDataFacade = new MenuDataFacade(categoryService, listingStore, itemClaimStore, moneyClaimStore, historyService);
        menuController = new MenuController(menuSessionManager, buildMenuRenderer());
        chatPromptService = new MarketChatPromptService(this, listingService, claimService, menuController, menuDataFacade);
        MenuClickRouter menuClickRouter = new MenuClickRouter(menuSessionManager, menuController, menuDataFacade, listingService, claimService, purchaseService, chatPromptService);
        getServer().getPluginManager().registerEvents(new MarketMenuListener(this, menuSessionManager, menuClickRouter), this);
        getServer().getPluginManager().registerEvents(chatPromptService, this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(menuSessionManager, chatPromptService), this);
    }

    private MenuRenderer buildMenuRenderer() {
        MenuVisualConfig menuVisualConfig = new MenuVisualConfigLoader(getLogger()).load(PluginDirectoryLayout.resolveMenuConfigFile(getDataFolder().toPath()));
        MenuItemFactory menuItemFactory = new MenuItemFactory(menuVisualConfig);
        return new MenuRenderer(menuDataFacade, menuItemFactory, menuVisualConfig);
    }

    private void reloadMenuRuntime() {
        if (menuController == null || menuDataFacade == null) {
            return;
        }
        menuController.updateRenderer(buildMenuRenderer());
    }

    public void reloadRuntimeData() {
        bootstrapFiles();
        loadAndCacheMainConfig();

        listingStore.reload();
        itemClaimStore.reload();
        moneyClaimStore.reload();
        salesStore.reload();
        adminSalesStore.reload();
        adminListingsStore.reload();
        adminClaimsStore.reload();
        recommendationHistoryStore.reload();

        flattenedMarketIndexService.reload();
        customItemRegistry.reload();
        enchantmentMetadataService.reload();
        if (categoryResolver instanceof DefaultCategoryResolver resolver) resolver.reload();
        if (categoryService instanceof DefaultCategoryService categorySvc) categorySvc.reload();
        saleHistoryIndex.reload();
        priceRecommendationService.reload();
        reloadMenuRuntime();
    }

    private void scheduleHourlyListingExpiration() {
        long oneHourTicks = 20L * 60L * 60L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                listingService.expireDueListings(System.currentTimeMillis());
            } catch (RuntimeException exception) {
                getLogger().warning("Hourly listing expiration pass failed: " + exception.getMessage());
            }
        }, oneHourTicks, oneHourTicks);
    }

    private void scheduleListingWriteFlush() {
        long fiveSecondsTicks = 20L * 5L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                listingStore.flushPendingWritesAsync();
            } catch (RuntimeException exception) {
                getLogger().warning("Queued listing write flush failed to start: " + exception.getMessage());
            }
        }, fiveSecondsTicks, fiveSecondsTicks);
    }

    private void scheduleItemClaimSoftLimitCleanup() {
        long fiveMinutesTicks = 20L * 60L * 5L;
        long thirtyMinutesTicks = 20L * 60L * 30L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                long softMaxBytes = ConfigService.get().itemClaimsSoftMaxBytes();
                if (softMaxBytes <= 0L) {
                    return;
                }

                long databaseBytes = sqliteStore.databaseFileSizeBytes();
                if (databaseBytes < softMaxBytes) {
                    return;
                }

                int deletedClaims = itemClaimStore.purgeOldestAbandonedClaims(System.currentTimeMillis());
                if (deletedClaims <= 0) {
                    warnAdminsAboutItemClaimStorage(databaseBytes, softMaxBytes);
                } else {
                    getLogger().info("Item-claim storage cleanup removed " + deletedClaims + " abandoned claim(s).");
                }
            } catch (RuntimeException exception) {
                getLogger().warning("Item-claim storage cleanup check failed: " + exception.getMessage());
            }
        }, fiveMinutesTicks, thirtyMinutesTicks);
    }

    private void warnAdminsAboutItemClaimStorage(long databaseBytes, long softMaxBytes) {
        String plain = "DivineMarketplace item-claim storage is above the soft limit and no abandoned claims were removed. Ask players to empty claim storage or raise storage.limits.itemClaimsSoftMaxMb.";
        getLogger().warning(plain + " Current database size: " + databaseBytes + " bytes; soft limit: " + softMaxBytes + " bytes.");

        String richMessage = "<yellow>DivineMarketplace item-claim storage is above the soft limit.</yellow> "
                + "<gray>No abandoned claims were available for cleanup. Ask players to empty claim storage or raise "
                + "storage.limits.itemClaimsSoftMaxMb.</gray>";
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission("divinemarketplace.admin")) {
                player.sendRichMessage(richMessage);
            }
        }
    }

    private void logStartupSummary(Logger logger) {
        Path dataFolder = getDataFolder().toPath();
        logger.info(() -> "Data folder: " + dataFolder.toAbsolutePath());
        logger.info(() -> "config.yml: " + dataFolder.resolve(PluginDirectoryLayout.CONFIG_YML).toAbsolutePath());
        logger.info(() -> "market.db: " + dataFolder.resolve(ConfigService.get().sqliteFile()).toAbsolutePath());
        logger.info(() -> "Runtime tables: market_index, market_prices, price_history, listings, item_claims, money_claims, sales, admin_sales, admin_listings, admin_claims, runtime_state, custom_enchants, custom_item_overrides");
        logger.info(() -> "Default listing duration days: " + mainConfig.listingPolicies().defaults().listingDurationDays());
        logger.info(() -> "Default max active listings: " + mainConfig.listingPolicies().defaults().maxListings());
        logger.info(() -> "Default UI sort mode: " + mainConfig.ui().defaultSortMode());
        logger.info(() -> "Market recalc interval hours: " + mainConfig.market().recalcIntervalHours());
        logger.info(() -> "Vault economy provider: " + economy.getClass().getName());
    }

    public Economy getEconomy() { return economy; }
    public ListingService getListingService() { return listingService; }
    public ClaimService getClaimService() { return claimService; }
    public PurchaseService getPurchaseService() { return purchaseService; }
    public MenuController getMenuController() { return menuController; }
    public ItemIdentityResolver getItemIdentityResolver() { return itemIdentityResolver; }
    public CategoryResolver getCategoryResolver() { return categoryResolver; }
    public CategoryService getCategoryService() { return categoryService; }
    public FlattenedMarketIndexService getFlattenedMarketIndexService() { return flattenedMarketIndexService; }
    public CustomItemRegistry getCustomItemRegistry() { return customItemRegistry; }
    public PriceRecommendationService getPriceRecommendationService() { return priceRecommendationService; }
    public MarketRecalculationService getMarketRecalculationService() { return marketRecalculationService; }
    public HistoryService getHistoryService() { return historyService; }
    public AdminHistoryService getAdminHistoryService() { return adminHistoryService; }
    public AdminHistoryExportService getAdminHistoryExportService() { return adminHistoryExportService; }
    public SQLiteListingStore getListingStore() { return listingStore; }
    public CustomItemCollisionLogService getCustomItemCollisionLogService() { return customItemCollisionLogService; }
    public CustomItemTypeExtractor getCustomItemTypeExtractor() { return customItemTypeExtractor; }
    public CustomItemMetadataLogService getCustomItemMetadataLogService() { return customItemMetadataLogService; }
    public SQLiteCustomItemOverrideStore getCustomItemOverrideStore() { return customItemOverrideStore; }
}
