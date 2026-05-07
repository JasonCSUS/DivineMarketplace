package divinejason.divinemarketplace.auction.service.listing;

/*
 * Layer : service
 * Owns  : listing write helper behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Contains helper logic for listing write that is shared by services with similar inventory/storage rules.
 */
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.identity.ListingPolicyResolver;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Shared create-or-merge listing persistence helper.
 *
 * Used by:
 * - main-hand listing flow
 * - claim-to-listing relist flow
 */
public final class ListingWriteHelper {
    private final SQLiteListingStore listingStore;
    private final CategoryService categoryService;

    public ListingWriteHelper(
            SQLiteListingStore listingStore,
            CategoryService categoryService
    ) {
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
    }

    /**
     * Applies the listing create/merge to memory only. Callers must enqueue the
     * returned listing as part of their own SQLiteWriteBatch.
     */
    public ListingWriteResult createOrMergeInMemory(
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
            listingStore.saveOrReplaceInMemory(savedListing);
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
            listingStore.saveOrReplaceInMemory(savedListing);
        }

        categoryService.refreshIndexesFor(savedListing.marketKey(), savedListing.categoryId());
        return new ListingWriteResult(savedListing, mergedIntoExisting, mergeTarget);
    }

    /** Creates the SQLite durability mutation for a listing write result. */
    public SQLiteMutation putMutation(ListingWriteResult writeResult) {
        return listingStore.putMutation(writeResult.listing());
    }

    /** Restores only the runtime cache after a failed pre-enqueue write flow. */
    public void rollbackWriteInMemory(ListingWriteResult writeResult) {
        if (writeResult.previousListing() == null) {
            listingStore.deleteInMemory(writeResult.listing().listingId());
        } else {
            listingStore.saveOrReplaceInMemory(writeResult.previousListing());
        }
        categoryService.refreshIndexesFor(writeResult.listing().marketKey(), writeResult.listing().categoryId());
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

    public record ListingWriteResult(Listing listing, boolean mergedIntoExisting, Listing previousListing) {
    }
}
