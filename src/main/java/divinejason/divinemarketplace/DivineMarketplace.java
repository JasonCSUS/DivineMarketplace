package divinejason.divinemarketplace;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import divinejason.divinemarketplace.auction.persistence.BinaryAdminClaimsStore;
import divinejason.divinemarketplace.auction.persistence.BinaryAdminListingsStore;
import divinejason.divinemarketplace.auction.persistence.BinaryAdminSalesStore;
import divinejason.divinemarketplace.auction.persistence.BinaryItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.BinaryListingStore;
import divinejason.divinemarketplace.auction.persistence.BinaryMoneyClaimStore;
import divinejason.divinemarketplace.auction.persistence.BinarySalesStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.AdminHistoryService;
import divinejason.divinemarketplace.auction.service.CategoryResolver;
import divinejason.divinemarketplace.auction.service.CategoryService;
import divinejason.divinemarketplace.auction.service.ClaimService;
import divinejason.divinemarketplace.auction.service.CustomItemDataSource;
import divinejason.divinemarketplace.auction.service.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.DefaultCategoryResolver;
import divinejason.divinemarketplace.auction.service.DefaultCategoryService;
import divinejason.divinemarketplace.auction.service.DefaultClaimService;
import divinejason.divinemarketplace.auction.service.DefaultCustomItemRegistry;
import divinejason.divinemarketplace.auction.service.DefaultItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.DefaultListingService;
import divinejason.divinemarketplace.auction.service.DefaultMarketAnalyticsService;
import divinejason.divinemarketplace.auction.service.DefaultPurchaseService;
import divinejason.divinemarketplace.auction.service.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.ListingPolicyResolver;
import divinejason.divinemarketplace.auction.service.ListingService;
import divinejason.divinemarketplace.auction.service.ListingWriteHelper;
import divinejason.divinemarketplace.auction.service.MarketAnalyticsService;
import divinejason.divinemarketplace.auction.service.NoopCustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.PurchaseService;
import divinejason.divinemarketplace.auction.service.StorageItemDeliveryHelper;
import divinejason.divinemarketplace.auction.service.YamlCustomItemDataSource;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.config.MainConfig;
import divinejason.divinemarketplace.config.MainConfigLoader;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import divinejason.divinemarketplace.setup.PluginFileInitializer;
import net.milkbowl.vault.economy.Economy;

/**
 * Main plugin entry point.
 *
 * Startup responsibility now includes:
 * - file/bootstrap/config load
 * - Vault economy hookup
 * - low-risk runtime service/store/helper wiring
 *
 * Note:
 * - some services are still intentionally placeholder-level while deeper browse/menu
 *   layers are not implemented yet
 */
public final class DivineMarketplace extends JavaPlugin {

    private PluginFileInitializer fileInitializer;
    private MainConfigLoader mainConfigLoader;
    private MainConfig mainConfig;

    private Economy economy;

    private BinaryListingStore listingStore;
    private BinaryItemClaimStore itemClaimStore;
    private BinaryMoneyClaimStore moneyClaimStore;
    private BinarySalesStore salesStore;
    private BinaryAdminSalesStore adminSalesStore;
    private BinaryAdminListingsStore adminListingsStore;
    private BinaryAdminClaimsStore adminClaimsStore;

    private CustomItemDataSource customItemDataSource;
    private CustomItemRegistry customItemRegistry;
    private CustomItemTypeExtractor customItemTypeExtractor;
    private CategoryResolver categoryResolver;
    private ItemIdentityResolver itemIdentityResolver;

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

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        logger.info("DivineMarketplace enabling...");

        try {
            bootstrapFiles();
            loadAndCacheMainConfig();
            setupEconomyOrThrow();
            initializeRuntimeObjects();
            logStartupSummary(logger);

            logger.info("DivineMarketplace enabled.");
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to enable DivineMarketplace.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        ConfigService.get().clear();
        mainConfig = null;
        getLogger().info("DivineMarketplace disabled.");
    }

    private void bootstrapFiles() {
        fileInitializer = new PluginFileInitializer(this);
        fileInitializer.initialize();
    }

    private void loadAndCacheMainConfig() {
        mainConfigLoader = new MainConfigLoader();
        mainConfig = mainConfigLoader.load(this);
        ConfigService.get().setMainConfig(mainConfig);
    }

    private void setupEconomyOrThrow() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            throw new IllegalStateException("Vault economy provider was not found.");
        }
        economy = provider.getProvider();
    }

    private void initializeRuntimeObjects() {
        listingStore = new BinaryListingStore(this);
        itemClaimStore = new BinaryItemClaimStore(this);
        moneyClaimStore = new BinaryMoneyClaimStore(this);
        salesStore = new BinarySalesStore(this);
        adminSalesStore = new BinaryAdminSalesStore(this);
        adminListingsStore = new BinaryAdminListingsStore(this);
        adminClaimsStore = new BinaryAdminClaimsStore(this);

        customItemDataSource = new YamlCustomItemDataSource(this);
        customItemRegistry = new DefaultCustomItemRegistry(customItemDataSource);
        customItemTypeExtractor = new NoopCustomItemTypeExtractor();
        categoryResolver = new DefaultCategoryResolver(this);
        itemIdentityResolver = new DefaultItemIdentityResolver(
                categoryResolver,
                customItemRegistry,
                customItemTypeExtractor
        );

        categoryService = new DefaultCategoryService();
        marketAnalyticsService = new DefaultMarketAnalyticsService(salesStore);
        adminHistoryService = new DefaultAdminHistoryService(
                adminSalesStore,
                adminListingsStore,
                adminClaimsStore
        );
        adminHistoryExportService = new DefaultAdminHistoryExportService(
                (DefaultAdminHistoryService) adminHistoryService,
                getDataFolder().toPath().resolve("logs").resolve("exports")
        );

        listingPolicyResolver = new ListingPolicyResolver();
        storageItemDeliveryHelper = new StorageItemDeliveryHelper();
        listingWriteHelper = new ListingWriteHelper(
                listingStore,
                categoryService,
                marketAnalyticsService
        );

        listingService = new DefaultListingService(
                listingStore,
                itemClaimStore,
                itemIdentityResolver,
                adminHistoryService,
                categoryService,
                marketAnalyticsService,
                listingPolicyResolver,
                listingWriteHelper
        );

        claimService = new DefaultClaimService(
                itemClaimStore,
                moneyClaimStore,
                itemIdentityResolver,
                adminHistoryService,
                economy,
                storageItemDeliveryHelper,
                listingPolicyResolver,
                listingWriteHelper
        );

        purchaseService = new DefaultPurchaseService(
                listingStore,
                itemClaimStore,
                moneyClaimStore,
                itemIdentityResolver,
                adminHistoryService,
                categoryService,
                marketAnalyticsService,
                storageItemDeliveryHelper,
                economy
        );
    }

    private void logStartupSummary(Logger logger) {
        Path dataFolder = getDataFolder().toPath();

        logger.info(() -> "Data folder: " + dataFolder.toAbsolutePath());
        logger.info(() -> "config.yml: " + dataFolder.resolve(PluginDirectoryLayout.CONFIG_YML).toAbsolutePath());
        logger.info(() -> "category_config.yml: " + dataFolder.resolve(PluginDirectoryLayout.CATEGORY_CONFIG_YML).toAbsolutePath());
        logger.info(() -> "Default listing duration days: " + mainConfig.listingPolicies().defaults().listingDurationDays());
        logger.info(() -> "Default max active listings: " + mainConfig.listingPolicies().defaults().maxListings());
        logger.info(() -> "Default UI sort mode: " + mainConfig.ui().defaultSortMode());
        logger.info(() -> "Market recalc interval hours: " + mainConfig.market().recalcIntervalHours());
        logger.info(() -> "Vault economy provider: " + economy.getClass().getName());
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ListingService getListingService() {
        return listingService;
    }

    public ClaimService getClaimService() {
        return claimService;
    }

    public PurchaseService getPurchaseService() {
        return purchaseService;
    }

    public ItemIdentityResolver getItemIdentityResolver() {
        return itemIdentityResolver;
    }

    public CategoryResolver getCategoryResolver() {
        return categoryResolver;
    }

    public CustomItemRegistry getCustomItemRegistry() {
        return customItemRegistry;
    }

    public AdminHistoryService getAdminHistoryService() {
        return adminHistoryService;
    }

    public AdminHistoryExportService getAdminHistoryExportService() {
        return adminHistoryExportService;
    }
}
