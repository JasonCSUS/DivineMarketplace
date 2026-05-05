package divinejason.divinemarketplace.config;

import divinejason.divinemarketplace.auction.model.SortMode;

import java.util.List;

public record MainConfig(
        Storage storage,
        CustomItems customItems,
        CustomItemIdentity customItemIdentity,
        ListingPolicies listingPolicies,
        Claims claims,
        Packages packagesConfig,
        Market market,
        Search search,
        Admin admin,
        Ui ui
) {
    public record Storage(Database database, Limits limits, Cleanup cleanup) {}
    public record Database(String sqliteFile, String modulePrefix) {}
    public record Limits(int salesHistoryMaxMb, int adminSalesHistoryMaxMb, int adminListingsHistoryMaxMb, int adminClaimsHistoryMaxMb, int itemClaimsSoftMaxMb) {}
    public record Cleanup(int abandonedItemClaimDays) {}
    public record CustomItems(boolean autoDiscoverUnknownItems, boolean autoWriteDefinitionsImmediately, String defaultCategory, boolean useClonedItemForPreviewTemplate, boolean requireAdminReviewForAllNewItems, boolean highPriorityOnlyForUnsafeResolution) {}
    public record CustomItemIdentity(UnknownCustomModelData unknownCustomModelData, MetadataSnapshots metadataSnapshots, List<Rule> rules) {}
    public record UnknownCustomModelData(boolean enabled, boolean autoCreateDefinition, String category) {}
    public record MetadataSnapshots(boolean writeUnknownSnapshots, boolean writeInspectRawSnapshots, String directory) {}
    public record Rule(String id, String source, String section, String key, String resultMode, String prefix, boolean appendMaterial, boolean appendCustomModelData) {}
    public record ListingPolicies(ListingPolicy defaults, List<ListingTier> tiers) {}
    public record ListingPolicy(int maxListings, int listingDurationDays) {}
    public record ListingTier(String permission, int maxListings, int listingDurationDays) {}
    public record Claims(boolean claimMenuUsesSafeChunkRedemption, boolean shiftClickClaimsAsMuchAsSafelyFits, int maxActiveItemClaims) {}
    public record Packages(String previewMode, boolean keepExactPayloadInFileStorage, boolean keepExactPayloadCachedInMemory) {}
    public record Market(int recalcIntervalHours, int perItemMinimumRecalcHours, int recalcItemsPerRun, int saleLookbackDays, Thresholds thresholds, Adjustment adjustment, Trend trend) {}
    public record Thresholds(double samePercent, double smallAdjustmentPercent, double mediumAdjustmentPercent, double majorOverpricedPercent, double majorUnderpricedPercent) {}
    public record Adjustment(double minimumPercent, double maximumPercent) {}
    public record Trend(double fitnessThreshold, int minimumSaleSamples, int minimumListingSamples) {}
    public record Search(boolean partialMatching, int minTokenLength, int maxResultsPerPage) {}
    public record Admin(boolean alertUnknownCustomItems, boolean alertUnknownCustomEnchants, boolean writeUnknownDefinitionsImmediately, boolean allowInGameDefinitionCommands, boolean regeneratePermissionsFileOnReload) {}
    public record Ui(SortMode defaultSortMode, boolean showEmptyTopLevelCategories, boolean showListingCountsInCategoryLore, boolean interceptAllInventoryClicks) {}
}
