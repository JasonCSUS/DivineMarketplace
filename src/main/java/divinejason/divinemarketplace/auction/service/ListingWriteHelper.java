package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.persistence.BinaryListingStore;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Shared create-or-merge listing persistence helper.
 *
 * Used by:
 * - main-hand listing flow
 * - claim-to-listing relist flow
 */
public final class ListingWriteHelper {
    private final BinaryListingStore listingStore;
    private final CategoryService categoryService;
    private final MarketAnalyticsService marketAnalyticsService;

    public ListingWriteHelper(
            BinaryListingStore listingStore,
            CategoryService categoryService,
            MarketAnalyticsService marketAnalyticsService
    ) {
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.marketAnalyticsService = Objects.requireNonNull(marketAnalyticsService, "marketAnalyticsService");
    }

    public ListingWriteResult createOrMerge(
            UUID sellerUuid,
            ItemStack normalizedSnapshot,
            int quantity,
            long unitPrice,
            ResolvedItemDefinition resolved,
            ListingPolicyResolver.ListingPolicy listingPolicy,
            long nowEpochMillis
    ) {
        long listingDurationMillis = listingPolicy.listingDurationDays() * 86_400_000L;

        Listing mergeTarget = listingStore.findMergeTarget(
                sellerUuid,
                resolved.marketKey(),
                resolved.marketDisplayName(),
                resolved.categoryId(),
                unitPrice,
                listingDurationMillis,
                normalizedSnapshot
        ).orElse(null);

        if (mergeTarget == null) {
            int activeListings = listingStore.countBySeller(sellerUuid);
            if (activeListings >= listingPolicy.maxListings()) {
                throw new ListingWriteException(
                        ListingCreateFailureReason.ACTIVE_LISTING_LIMIT_REACHED,
                        "Seller is already at the active listing limit."
                );
            }
        }

        boolean mergedIntoExisting;
        Listing savedListing;

        if (mergeTarget != null) {
            mergedIntoExisting = true;
            savedListing = new Listing(
                    mergeTarget.listingId(),
                    mergeTarget.sellerUuid(),
                    mergeTarget.listedItemSnapshot(),
                    mergeTarget.amount() + quantity,
                    mergeTarget.marketKey(),
                    mergeTarget.marketDisplayName(),
                    mergeTarget.categoryId(),
                    mergeTarget.unitPrice(),
                    nowEpochMillis,
                    mergeTarget.listingDurationMillis()
            );
            listingStore.saveOrReplace(savedListing);
        } else {
            mergedIntoExisting = false;
            savedListing = new Listing(
                    UUID.randomUUID(),
                    sellerUuid,
                    normalizedSnapshot.clone(),
                    quantity,
                    resolved.marketKey(),
                    resolved.marketDisplayName(),
                    resolved.categoryId(),
                    unitPrice,
                    nowEpochMillis,
                    listingDurationMillis
            );
            listingStore.saveOrReplace(savedListing);
            marketAnalyticsService.recordListingCreated(savedListing);
        }

        categoryService.refreshIndexesFor(savedListing.marketKey(), savedListing.categoryId());
        return new ListingWriteResult(savedListing, mergedIntoExisting);
    }

    public static final class ListingWriteException extends RuntimeException {
        private final ListingCreateFailureReason failureReason;

        public ListingWriteException(ListingCreateFailureReason failureReason, String message) {
            super(message);
            this.failureReason = failureReason;
        }

        public ListingCreateFailureReason failureReason() {
            return failureReason;
        }
    }

    public record ListingWriteResult(Listing listing, boolean mergedIntoExisting) {
    }
}
