package divinejason.divinemarketplace.config;

import divinejason.divinemarketplace.auction.model.SortMode;

import java.util.List;

public final class ConfigService {
    private static final ConfigService INSTANCE = new ConfigService();
    private MainConfig mainConfig;
    private ConfigService() {}
    public static ConfigService get() { return INSTANCE; }
    public void setMainConfig(MainConfig mainConfig) { this.mainConfig = mainConfig; }
    public void clear() { this.mainConfig = null; }
    public MainConfig getMainConfig() { if (mainConfig == null) throw new IllegalStateException("MainConfig has not been loaded yet."); return mainConfig; }
    public String sqliteFile() { return getMainConfig().storage().database().sqliteFile(); }
    public String sqliteModulePrefix() { return getMainConfig().storage().database().modulePrefix(); }
    public long listingDefaultDurationMillis() { return getMainConfig().listingPolicies().defaults().listingDurationDays() * 86_400_000L; }
    public int listingDefaultMaxActiveListings() { return getMainConfig().listingPolicies().defaults().maxListings(); }
    public long itemClaimAbandonMillis() { return getMainConfig().storage().cleanup().abandonedItemClaimDays() * 86_400_000L; }
    public int salesHistoryMaxMb() { return getMainConfig().storage().limits().salesHistoryMaxMb(); }
    public int adminSalesHistoryMaxMb() { return getMainConfig().storage().limits().adminSalesHistoryMaxMb(); }
    public int adminListingsHistoryMaxMb() { return getMainConfig().storage().limits().adminListingsHistoryMaxMb(); }
    public int adminClaimsHistoryMaxMb() { return getMainConfig().storage().limits().adminClaimsHistoryMaxMb(); }
    public int itemClaimsSoftMaxMb() { return getMainConfig().storage().limits().itemClaimsSoftMaxMb(); }
    public long marketRecalcIntervalMillis() { return getMainConfig().market().recalcIntervalHours() * 3_600_000L; }
    public long marketProfileMinimumRecalcMillis() { return getMainConfig().market().perItemMinimumRecalcHours() * 3_600_000L; }
    public int marketRecalcItemsPerRun() { return getMainConfig().market().recalcItemsPerRun(); }
    public long marketSaleLookbackMillis() { return getMainConfig().market().saleLookbackDays() * 86_400_000L; }
    public double marketTrendFitnessThreshold() { return getMainConfig().market().trend().fitnessThreshold(); }
    public int marketMinimumSaleSamples() { return getMainConfig().market().trend().minimumSaleSamples(); }
    public int marketMinimumListingSamples() { return getMainConfig().market().trend().minimumListingSamples(); }
    public double marketSamePercent() { return getMainConfig().market().thresholds().samePercent(); }
    public double marketSmallAdjustmentPercent() { return getMainConfig().market().thresholds().smallAdjustmentPercent(); }
    public double marketMediumAdjustmentPercent() { return getMainConfig().market().thresholds().mediumAdjustmentPercent(); }
    public double marketMajorOverpricedPercent() { return getMainConfig().market().thresholds().majorOverpricedPercent(); }
    public double marketMajorUnderpricedPercent() { return getMainConfig().market().thresholds().majorUnderpricedPercent(); }
    public double marketMinimumAdjustmentPercent() { return getMainConfig().market().adjustment().minimumPercent(); }
    public double marketMaximumAdjustmentPercent() { return getMainConfig().market().adjustment().maximumPercent(); }
    public int searchMinTokenLength() { return getMainConfig().search().minTokenLength(); }
    public int searchMaxResultsPerPage() { return getMainConfig().search().maxResultsPerPage(); }
    public SortMode defaultSortMode() { return getMainConfig().ui().defaultSortMode(); }
    public boolean showEmptyTopLevelCategories() { return getMainConfig().ui().showEmptyTopLevelCategories(); }
    public boolean showListingCountsInCategoryLore() { return getMainConfig().ui().showListingCountsInCategoryLore(); }
    public boolean interceptAllInventoryClicks() { return getMainConfig().ui().interceptAllInventoryClicks(); }
    public boolean alertUnknownCustomItems() { return getMainConfig().admin().alertUnknownCustomItems(); }
    public boolean alertUnknownCustomEnchants() { return getMainConfig().admin().alertUnknownCustomEnchants(); }
    public boolean writeUnknownDefinitionsImmediately() { return getMainConfig().admin().writeUnknownDefinitionsImmediately(); }
    public boolean allowInGameDefinitionCommands() { return getMainConfig().admin().allowInGameDefinitionCommands(); }
    public boolean regeneratePermissionsFileOnReload() { return getMainConfig().admin().regeneratePermissionsFileOnReload(); }
    public boolean unknownCustomModelDataEnabled() { return getMainConfig().customItemIdentity().unknownCustomModelData().enabled(); }
    public boolean autoCreateUnknownCustomModelDefinitions() { return getMainConfig().customItemIdentity().unknownCustomModelData().autoCreateDefinition(); }
    public String unknownCustomModelCategory() { return getMainConfig().customItemIdentity().unknownCustomModelData().category(); }
    public boolean writeUnknownMetadataSnapshots() { return getMainConfig().customItemIdentity().metadataSnapshots().writeUnknownSnapshots(); }
    public boolean writeInspectRawSnapshots() { return getMainConfig().customItemIdentity().metadataSnapshots().writeInspectRawSnapshots(); }
    public String metadataSnapshotDirectory() { return getMainConfig().customItemIdentity().metadataSnapshots().directory(); }
    public List<MainConfig.Rule> customIdentityRules() { return getMainConfig().customItemIdentity().rules(); }
}
