package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.persistence.BinaryItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.BinaryListingStore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;
import java.util.UUID;

/**
 * Concrete listing lifecycle implementation.
 *
 * Shared helper note:
 * - the actual create-or-merge listing persistence logic now lives in ListingWriteHelper
 * - this class stays focused on main-hand validation/removal and cancel/expire flow
 */
public final class DefaultListingService implements ListingService {
    private static final String ADMIN_CANCEL_PERMISSION = "divinemarketplace.admin.listing.cancel";
    private static final String ADMIN_PERMISSION = "divinemarketplace.admin";

    private final BinaryListingStore listingStore;
    private final BinaryItemClaimStore itemClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final AdminHistoryService adminHistoryService;
    private final CategoryService categoryService;
    private final MarketAnalyticsService marketAnalyticsService;
    private final ListingPolicyResolver listingPolicyResolver;
    private final ListingWriteHelper listingWriteHelper;

    public DefaultListingService(
            BinaryListingStore listingStore,
            BinaryItemClaimStore itemClaimStore,
            ItemIdentityResolver itemIdentityResolver,
            AdminHistoryService adminHistoryService,
            CategoryService categoryService,
            MarketAnalyticsService marketAnalyticsService,
            ListingPolicyResolver listingPolicyResolver,
            ListingWriteHelper listingWriteHelper
    ) {
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.itemIdentityResolver = Objects.requireNonNull(itemIdentityResolver, "itemIdentityResolver");
        this.adminHistoryService = Objects.requireNonNull(adminHistoryService, "adminHistoryService");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.marketAnalyticsService = Objects.requireNonNull(marketAnalyticsService, "marketAnalyticsService");
        this.listingPolicyResolver = Objects.requireNonNull(listingPolicyResolver, "listingPolicyResolver");
        this.listingWriteHelper = Objects.requireNonNull(listingWriteHelper, "listingWriteHelper");
    }

    @Override
    public ListingCreateResult createOrMergeListing(Player seller, ItemStack sourceItem, int quantity, long unitPrice) {
        if (seller == null) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "Seller was null.");
        }
        if (sourceItem == null || sourceItem.getType().isAir()) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "sourceItem was null or air.");
        }
        if (quantity <= 0) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "Requested quantity must be > 0.");
        }
        if (unitPrice <= 0L) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "Unit price must be > 0.");
        }

        PlayerInventory inventory = seller.getInventory();
        SourceSelection selection;
        try {
            selection = selectMainHandSource(inventory, sourceItem);
        } catch (ListingCreateFailure failure) {
            return ListingCreateResult.failure(failure.reason(), quantity, 0, failure.getMessage());
        }

        int clampedQuantity = Math.min(quantity, selection.liveStack().getAmount());
        if (clampedQuantity <= 0) {
            return ListingCreateResult.failure(ListingCreateFailureReason.MAIN_HAND_EMPTY, quantity, 0, "Main-hand stack has no listable quantity.");
        }

        ItemStack normalizedSnapshot = normalizeSnapshotAmount(selection.liveStack());
        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizedSnapshot.clone());
        if (resolved == null) {
            return ListingCreateResult.failure(ListingCreateFailureReason.ITEM_IDENTITY_UNRESOLVED, quantity, clampedQuantity, "ItemIdentityResolver returned null.");
        }

        ListingPolicyResolver.ListingPolicy listingPolicy = listingPolicyResolver.resolve(seller);
        long nowEpochMillis = System.currentTimeMillis();

        RemovalReservation reservation;
        try {
            reservation = removeExactQuantityFromSlot(inventory, selection, clampedQuantity);
        } catch (ListingCreateFailure failure) {
            return ListingCreateResult.failure(failure.reason(), quantity, clampedQuantity, failure.getMessage());
        }

        try {
            ListingWriteHelper.ListingWriteResult writeResult = listingWriteHelper.createOrMerge(
                    seller.getUniqueId(),
                    normalizedSnapshot,
                    clampedQuantity,
                    unitPrice,
                    resolved,
                    listingPolicy,
                    nowEpochMillis
            );

            Listing savedListing = writeResult.listing();

            adminHistoryService.recordListing(buildListingAdminRecord(
                    AdminTransactionType.LIST,
                    savedListing,
                    seller.getUniqueId(),
                    nowEpochMillis,
                    writeResult.mergedIntoExisting() ? "MERGED" : "CREATED",
                    writeResult.mergedIntoExisting() ? "USER_MERGE" : "USER_LIST"
            ));

            return ListingCreateResult.success(
                    savedListing.listingId(),
                    writeResult.mergedIntoExisting(),
                    quantity,
                    clampedQuantity,
                    savedListing.marketKey(),
                    savedListing.marketDisplayName(),
                    savedListing.categoryId()
            );
        } catch (ListingWriteHelper.ListingWriteException exception) {
            restoreReservation(inventory, reservation);
            return ListingCreateResult.failure(exception.failureReason(), quantity, clampedQuantity, exception.getMessage());
        } catch (RuntimeException exception) {
            restoreReservation(inventory, reservation);
            return ListingCreateResult.failure(ListingCreateFailureReason.INTERNAL_ERROR, quantity, clampedQuantity, exception.getMessage());
        }
    }

    @Override
    public void cancelListing(Player actor, UUID listingId) {
        if (actor == null) {
            throw new IllegalArgumentException("actor cannot be null");
        }

        Listing listing = listingStore.findById(listingId)
                .orElseThrow(() -> new IllegalStateException("Listing not found: " + listingId));

        if (!mayCancel(actor, listing)) {
            throw new IllegalStateException("Actor is not allowed to cancel this listing.");
        }

        listingStore.delete(listing.listingId());

        long nowEpochMillis = System.currentTimeMillis();
        createOrMergeItemClaim(
                listing.sellerUuid(),
                listing.listedItemSnapshot(),
                listing.amount(),
                nowEpochMillis
        );

        adminHistoryService.recordListing(buildListingAdminRecord(
                AdminTransactionType.CANCEL,
                listing,
                actor.getUniqueId(),
                nowEpochMillis,
                "CANCELLED",
                actor.getUniqueId().equals(listing.sellerUuid()) ? "SELLER_CANCEL" : "ADMIN_CANCEL"
        ));

        marketAnalyticsService.recordListingCancelled(listing);
        categoryService.refreshIndexesFor(listing.marketKey(), listing.categoryId());
    }

    @Override
    public void expireDueListings(long nowEpochMillis) {
        Iterable<Listing> active = listingStore.getAllActive();
        for (Listing listing : active) {
            long expiresAt = listing.listedAtEpochMillis() + listing.listingDurationMillis();
            if (nowEpochMillis < expiresAt) {
                continue;
            }

            listingStore.delete(listing.listingId());

            createOrMergeItemClaim(
                    listing.sellerUuid(),
                    listing.listedItemSnapshot(),
                    listing.amount(),
                    nowEpochMillis
            );

            adminHistoryService.recordListing(buildListingAdminRecord(
                    AdminTransactionType.EXPIRE,
                    listing,
                    listing.sellerUuid(),
                    nowEpochMillis,
                    "EXPIRED",
                    "LISTING_DURATION_ELAPSED"
            ));

            marketAnalyticsService.recordListingExpired(listing);
            categoryService.refreshIndexesFor(listing.marketKey(), listing.categoryId());
        }
    }

    private SourceSelection selectMainHandSource(PlayerInventory inventory, ItemStack sourceItem) {
        int heldSlot = inventory.getHeldItemSlot();
        ItemStack[] storageContents = inventory.getStorageContents();

        if (heldSlot < 0 || heldSlot >= storageContents.length) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_STACK_CHANGED, "Held slot index is outside storage contents.");
        }

        ItemStack held = storageContents[heldSlot];
        if (held == null || held.getType().isAir()) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_EMPTY, "Main hand is empty.");
        }

        ItemStack heldComparable = normalizeSnapshotAmount(held);
        ItemStack requestedComparable = normalizeSnapshotAmount(sourceItem);

        if (!heldComparable.isSimilar(requestedComparable)) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_ITEM_MISMATCH, "Main-hand item no longer matches the requested listing item.");
        }

        return new SourceSelection(heldSlot, held.clone());
    }

    private RemovalReservation removeExactQuantityFromSlot(PlayerInventory inventory, SourceSelection selection, int quantity) {
        ItemStack[] storageContents = inventory.getStorageContents();
        ItemStack current = storageContents[selection.slotIndex()];

        if (current == null || current.getType().isAir()) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_STACK_CHANGED, "Main-hand source stack vanished before removal.");
        }

        ItemStack liveComparable = normalizeSnapshotAmount(current);
        ItemStack expectedComparable = normalizeSnapshotAmount(selection.liveStack());

        if (!liveComparable.isSimilar(expectedComparable)) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_STACK_CHANGED, "Main-hand source stack changed before removal.");
        }

        if (current.getAmount() < quantity) {
            throw new ListingCreateFailure(ListingCreateFailureReason.MAIN_HAND_STACK_CHANGED, "Main-hand stack no longer has enough quantity.");
        }

        ItemStack original = current.clone();
        int remainingAmount = current.getAmount() - quantity;
        storageContents[selection.slotIndex()] = remainingAmount > 0 ? cloneWithAmount(current, remainingAmount) : null;
        inventory.setStorageContents(storageContents);

        return new RemovalReservation(selection.slotIndex(), original);
    }

    private void restoreReservation(PlayerInventory inventory, RemovalReservation reservation) {
        ItemStack[] storageContents = inventory.getStorageContents();
        storageContents[reservation.slotIndex()] = reservation.originalStack().clone();
        inventory.setStorageContents(storageContents);
    }

    private void createOrMergeItemClaim(UUID ownerUuid, ItemStack baseSnapshot, int amount, long createdAtEpochMillis) {
        ItemStack normalizedSnapshot = normalizeSnapshotAmount(baseSnapshot);
        ItemClaimRecord incomingClaim = new ItemClaimRecord(
                UUID.randomUUID(),
                ownerUuid,
                normalizedSnapshot,
                amount,
                createdAtEpochMillis
        );
        itemClaimStore.mergeOrCreate(incomingClaim);
    }

    private AdminTransactionRecord buildListingAdminRecord(
            AdminTransactionType type,
            Listing listing,
            UUID actorUuid,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new AdminTransactionRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                type,
                listing.listingId().toString(),
                listing.sellerUuid(),
                null,
                actorUuid,
                listing.marketKey(),
                listing.marketDisplayName(),
                listing.categoryId(),
                listing.marketDisplayName() + " x" + listing.amount(),
                listing.amount(),
                listing.unitPrice() * listing.amount(),
                listing.unitPrice(),
                status,
                reason
        );
    }

    private boolean mayCancel(Player actor, Listing listing) {
        return actor.getUniqueId().equals(listing.sellerUuid())
                || actor.hasPermission(ADMIN_PERMISSION)
                || actor.hasPermission(ADMIN_CANCEL_PERMISSION);
    }

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }

    private ItemStack cloneWithAmount(ItemStack input, int amount) {
        ItemStack copy = input.clone();
        copy.setAmount(amount);
        return copy;
    }

    private record SourceSelection(int slotIndex, ItemStack liveStack) {
    }

    private record RemovalReservation(int slotIndex, ItemStack originalStack) {
    }

    private static final class ListingCreateFailure extends RuntimeException {
        private final ListingCreateFailureReason reason;

        private ListingCreateFailure(ListingCreateFailureReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        private ListingCreateFailureReason reason() {
            return reason;
        }
    }
}
