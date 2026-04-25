package divinejason.divinemarketplace.config;

import divinejason.divinemarketplace.auction.model.SortMode;

import java.util.List;

/**
 * Typed runtime representation of config.yml only.
 */
public record MainConfig(
        Storage storage,
        CustomItems customItems,
        ListingPolicies listingPolicies,
        Claims claims,
        Packages packagesConfig,
        Market market,
        Search search,
        Admin admin,
        Ui ui
) {
    public record Storage(
            Limits limits,
            Cleanup cleanup
    ) {
    }

    public record Limits(
            int salesHistoryMaxMb,
            int adminSalesHistoryMaxMb,
            int adminListingsHistoryMaxMb,
            int adminClaimsHistoryMaxMb,
            int itemClaimsSoftMaxMb
    ) {
    }

    public record Cleanup(
            int abandonedItemClaimDays
    ) {
    }

    public record CustomItems(
            boolean autoDiscoverUnknownItems,
            boolean autoWriteDefinitionsImmediately,
            String defaultCategory,
            boolean useClonedItemForPreviewTemplate,
            boolean requireAdminReviewForAllNewItems,
            boolean highPriorityOnlyForUnsafeResolution
    ) {
    }

    public record ListingPolicies(
            ListingPolicy defaults,
            List<ListingTier> tiers
    ) {
    }

    public record ListingPolicy(
            int maxListings,
            int listingDurationDays
    ) {
    }

    public record ListingTier(
            String permission,
            int maxListings,
            int listingDurationDays
    ) {
    }

    public record Claims(
            boolean claimMenuUsesSafeChunkRedemption,
            boolean shiftClickClaimsAsMuchAsSafelyFits
    ) {
    }

    public record Packages(
            String previewMode,
            boolean keepExactPayloadInFileStorage,
            boolean keepExactPayloadCachedInMemory
    ) {
    }

    public record Market(
            int recalcIntervalHours,
            int perItemMinimumRecalcHours,
            int recalcItemsPerRun,
            int saleLookbackDays,
            Thresholds thresholds,
            Adjustment adjustment,
            Trend trend
    ) {
    }

    public record Thresholds(
            double samePercent,
            double smallAdjustmentPercent,
            double mediumAdjustmentPercent,
            double majorOverpricedPercent,
            double majorUnderpricedPercent
    ) {
    }

    public record Adjustment(
            double minimumPercent,
            double maximumPercent
    ) {
    }

    public record Trend(
            double fitnessThreshold,
            int minimumSaleSamples,
            int minimumListingSamples
    ) {
    }

    public record Search(
            boolean partialMatching,
            int minTokenLength,
            int maxResultsPerPage
    ) {
    }

    public record Admin(
            boolean alertUnknownCustomItems,
            boolean alertUnknownCustomEnchants,
            boolean writeUnknownDefinitionsImmediately,
            boolean allowInGameDefinitionCommands,
            boolean regeneratePermissionsFileOnReload
    ) {
    }

    public record Ui(
            SortMode defaultSortMode,
            boolean showEmptyTopLevelCategories,
            boolean showListingCountsInCategoryLore,
            boolean interceptAllInventoryClicks
    ) {
    }
}
