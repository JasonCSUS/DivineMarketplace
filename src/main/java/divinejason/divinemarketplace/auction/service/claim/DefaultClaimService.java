package divinejason.divinemarketplace.auction.service.claim;

/*
 * Layer : service
 * Owns  : claim behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Implements claim service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.ClaimItemResult;
import divinejason.divinemarketplace.auction.model.ClaimMoneyResult;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.service.identity.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.identity.ListingPolicyResolver;
import divinejason.divinemarketplace.auction.service.listing.ListingWriteHelper;
import divinejason.divinemarketplace.auction.service.purchase.MarketWriteTransaction;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Concrete claim lifecycle implementation.
 *
 * Shared helper note:
 * - relist-from-claim uses ListingWriteHelper instead of duplicating listing persistence rules
 * - item delivery uses StorageItemDeliveryHelper instead of duplicating storage-contents logic
 *
 * Event policy:
 * - one MarketEventRecord is written per claim/relist action via marketEventService
 */
public final class DefaultClaimService implements ClaimService {
    private final Logger logger = Logger.getLogger(DefaultClaimService.class.getName());
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final MarketEventService marketEventService;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final Economy economy;
    private final StorageItemDeliveryHelper storageItemDeliveryHelper;
    private final ListingPolicyResolver listingPolicyResolver;
    private final ListingWriteHelper listingWriteHelper;

    public DefaultClaimService(
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            MarketEventService marketEventService,
            SQLiteWriteBehindQueue writeBehindQueue,
            ItemIdentityResolver itemIdentityResolver,
            Economy economy,
            StorageItemDeliveryHelper storageItemDeliveryHelper,
            ListingPolicyResolver listingPolicyResolver,
            ListingWriteHelper listingWriteHelper
    ) {
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
        this.itemIdentityResolver = Objects.requireNonNull(itemIdentityResolver, "itemIdentityResolver");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.storageItemDeliveryHelper = Objects.requireNonNull(storageItemDeliveryHelper, "storageItemDeliveryHelper");
        this.listingPolicyResolver = Objects.requireNonNull(listingPolicyResolver, "listingPolicyResolver");
        this.listingWriteHelper = Objects.requireNonNull(listingWriteHelper, "listingWriteHelper");
    }

    @Override
    public ClaimItemResult claimOneChunk(Player player, UUID claimId) {
        requirePlayer(player);

        ItemClaimRecord claim = loadOwnedClaim(player, claimId);
        if (claim == null) {
            return ClaimItemResult.failure(claimId, null, 0, "Claim was not found for this player.");
        }

        int deliverAmount = Math.min(claim.amount(), maxPerSlot(claim.claimItemSnapshot()));
        if (!storageItemDeliveryHelper.canFitAmount(player.getInventory(), claim.claimItemSnapshot(), deliverAmount)) {
            return ClaimItemResult.failure(claimId, displayNameFor(claim), deliverAmount, "Inventory does not have enough free storage space for one safe claim chunk.");
        }

        return deliverItemClaim(player, claim, deliverAmount, "ONE_CHUNK");
    }

    @Override
    public ClaimItemResult claimAsMuchAsFits(Player player, UUID claimId) {
        requirePlayer(player);

        ItemClaimRecord claim = loadOwnedClaim(player, claimId);
        if (claim == null) {
            return ClaimItemResult.failure(claimId, null, 0, "Claim was not found for this player.");
        }

        int deliverAmount = storageItemDeliveryHelper.maxInsertableAmount(player.getInventory(), claim.claimItemSnapshot(), claim.amount());
        if (deliverAmount <= 0) {
            return ClaimItemResult.failure(claimId, displayNameFor(claim), claim.amount(), "Inventory does not have enough free storage space to claim this item.");
        }

        return deliverItemClaim(player, claim, deliverAmount, "AS_MUCH_AS_FITS");
    }

    @Override
    public ListingCreateResult relistClaim(Player player, UUID claimId, int quantity, long unitPrice) {
        if (player == null) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "player was null");
        }
        if (quantity <= 0 || unitPrice <= 0L) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INVALID_REQUEST, quantity, 0, "quantity and unitPrice must be positive");
        }

        Optional<ItemClaimRecord> optionalClaim = itemClaimStore.findById(claimId, player.getUniqueId());
        if (optionalClaim.isEmpty()) {
            return ListingCreateResult.failure(ListingCreateFailureReason.CLAIM_NOT_FOUND, quantity, 0, "Claim was not found for this player.");
        }

        ItemClaimRecord claim = optionalClaim.get();
        int clampedQuantity = Math.min(quantity, claim.amount());
        if (clampedQuantity <= 0) {
            return ListingCreateResult.failure(ListingCreateFailureReason.CLAIM_EMPTY, quantity, 0, "Claim amount was zero.");
        }

        ItemStack normalizedSnapshot = normalizeSnapshotAmount(claim.claimItemSnapshot());
        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizedSnapshot.clone());
        if (resolved == null) {
            return ListingCreateResult.failure(ListingCreateFailureReason.ITEM_IDENTITY_UNRESOLVED, quantity, clampedQuantity, "ItemIdentityResolver returned null.");
        }

        ListingPolicyResolver.ListingPolicy listingPolicy = listingPolicyResolver.resolve(player);
        long nowEpochMillis = System.currentTimeMillis();
        MarketWriteTransaction transaction = new MarketWriteTransaction(logger);

        try {
            ListingWriteHelper.ListingWriteResult writeResult = listingWriteHelper.createOrMergeInMemory(
                    player.getUniqueId(),
                    normalizedSnapshot,
                    clampedQuantity,
                    unitPrice,
                    resolved,
                    listingPolicy,
                    nowEpochMillis
            );
            registerListingWriteRollback(transaction, writeResult);

            SQLiteMutation claimMutation = persistClaimAfterReductionWithRollback(transaction, claim, clampedQuantity);

            MarketEventRecord claimReductionEvent = buildItemClaimEvent(
                    player.getUniqueId(),
                    resolved,
                    clampedQuantity,
                    nowEpochMillis,
                    "RELISTED",
                    "CLAIM_TO_LISTING"
            );
            marketEventService.appendInMemory(claimReductionEvent);
            transaction.onRollback(() -> marketEventService.deleteInMemory(claimReductionEvent.eventId()));

            MarketEventRecord listEvent = buildListingEvent(
                    writeResult.listing(),
                    player.getUniqueId(),
                    nowEpochMillis,
                    writeResult.mergedIntoExisting() ? "MERGED" : "CREATED",
                    "CLAIM_RELIST"
            );
            marketEventService.appendInMemory(listEvent);
            transaction.onRollback(() -> marketEventService.deleteInMemory(listEvent.eventId()));

            writeBehindQueue.enqueue(SQLiteWriteBatch.builder("relist claim " + claim.claimId())
                    .add(listingWriteHelper.putMutation(writeResult))
                    .add(claimMutation)
                    .add(marketEventService.putMutation(claimReductionEvent))
                    .add(marketEventService.putMutation(listEvent))
                    .build());

            transaction.commit();

            return ListingCreateResult.success(
                    writeResult.listing().listingId(),
                    writeResult.mergedIntoExisting(),
                    quantity,
                    clampedQuantity,
                    writeResult.listing().marketKey(),
                    writeResult.listing().marketDisplayName(),
                    writeResult.listing().categoryId()
            );
        } catch (ListingWriteHelper.ListingWriteException exception) {
            transaction.rollbackQuietly();
            return ListingCreateResult.failure(exception.failureReason(), quantity, clampedQuantity, exception.getMessage());
        } catch (RuntimeException exception) {
            transaction.rollbackQuietly();
            return ListingCreateResult.failure(ListingCreateFailureReason.INTERNAL_ERROR, quantity, clampedQuantity, exception.getMessage());
        }
    }

    @Override
    public ClaimMoneyResult claimEarnings(Player player) {
        requirePlayer(player);

        long pendingAmount = moneyClaimStore.getBalanceOrZero(player.getUniqueId());
        if (pendingAmount <= 0L) {
            return ClaimMoneyResult.success(0L);
        }

        double payoutAmount = pendingAmount / 100.0;
        MarketWriteTransaction transaction = new MarketWriteTransaction(logger);

        try {
            moneyClaimStore.setBalanceInMemory(player.getUniqueId(), 0L);
            transaction.onRollback(() -> moneyClaimStore.setBalanceInMemory(player.getUniqueId(), pendingAmount));

            EconomyResponse response = economy.depositPlayer(player, payoutAmount);
            if (!response.transactionSuccess()) {
                throw new IllegalStateException("Vault deposit failed: " + response.errorMessage);
            }
            transaction.onRollback(() -> economy.withdrawPlayer(player, payoutAmount));

            MarketEventRecord moneyEvent = buildMoneyClaimEvent(
                    player.getUniqueId(),
                    pendingAmount,
                    System.currentTimeMillis(),
                    "PAID_OUT",
                    "CLAIM_EARNINGS"
            );
            marketEventService.appendInMemory(moneyEvent);
            transaction.onRollback(() -> marketEventService.deleteInMemory(moneyEvent.eventId()));

            writeBehindQueue.enqueue(SQLiteWriteBatch.builder("claim money " + player.getUniqueId())
                    .add(moneyClaimStore.putOrDeleteMutation(player.getUniqueId(), 0L))
                    .add(marketEventService.putMutation(moneyEvent))
                    .build());

            transaction.commit();
            return ClaimMoneyResult.success(pendingAmount);
        } catch (RuntimeException exception) {
            transaction.rollbackQuietly();
            return ClaimMoneyResult.failure(pendingAmount, exception.getMessage());
        }
    }

    /**
     * Performs the durable reduction, event write, and exact inventory insertion for item claims.
     * The claim store is updated before inventory insertion so rollback can restore the durable claim.
     */
    private ClaimItemResult deliverItemClaim(Player player, ItemClaimRecord claim, int deliverAmount, String reason) {
        MarketWriteTransaction transaction = new MarketWriteTransaction(logger);
        try {
            SQLiteMutation claimMutation = persistClaimAfterReductionWithRollback(transaction, claim, deliverAmount);

            ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizeSnapshotAmount(claim.claimItemSnapshot()));
            MarketEventRecord claimEvent = buildItemClaimEvent(
                    claim.ownerUuid(),
                    resolved,
                    deliverAmount,
                    System.currentTimeMillis(),
                    "CLAIMED",
                    reason
            );
            marketEventService.appendInMemory(claimEvent);
            transaction.onRollback(() -> marketEventService.deleteInMemory(claimEvent.eventId()));

            storageItemDeliveryHelper.insertExactQuantity(player.getInventory(), claim.claimItemSnapshot(), deliverAmount);

            writeBehindQueue.enqueue(SQLiteWriteBatch.builder("claim item " + claim.claimId())
                    .add(claimMutation)
                    .add(marketEventService.putMutation(claimEvent))
                    .build());

            transaction.commit();

            int remainingAmount = Math.max(0, claim.amount() - deliverAmount);
            return ClaimItemResult.success(
                    claim.claimId(),
                    resolved.marketDisplayName(),
                    deliverAmount,
                    deliverAmount,
                    remainingAmount
            );
        } catch (RuntimeException exception) {
            transaction.rollbackQuietly();
            return ClaimItemResult.failure(claim.claimId(), displayNameFor(claim), deliverAmount, exception.getMessage());
        }
    }

    private void requirePlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
    }

    private ItemClaimRecord loadOwnedClaim(Player player, UUID claimId) {
        if (claimId == null) {
            return null;
        }
        return itemClaimStore.findById(claimId, player.getUniqueId()).orElse(null);
    }

    private String displayNameFor(ItemClaimRecord claim) {
        if (claim == null) {
            return null;
        }
        try {
            return itemIdentityResolver.resolve(normalizeSnapshotAmount(claim.claimItemSnapshot())).marketDisplayName();
        } catch (RuntimeException ignored) {
            return claim.claimItemSnapshot().getType().name();
        }
    }

    private SQLiteMutation persistClaimAfterReductionWithRollback(MarketWriteTransaction transaction, ItemClaimRecord originalClaim, int deliveredAmount) {
        int remainingAmount = originalClaim.amount() - deliveredAmount;
        SQLiteMutation mutation;
        if (remainingAmount <= 0) {
            itemClaimStore.deleteInMemory(originalClaim.claimId(), originalClaim.ownerUuid());
            mutation = itemClaimStore.deleteMutation(originalClaim.claimId());
        } else {
            ItemClaimRecord updatedClaim = new ItemClaimRecord(
                    originalClaim.claimId(),
                    originalClaim.ownerUuid(),
                    originalClaim.claimItemSnapshot(),
                    remainingAmount,
                    originalClaim.createdAtEpochMillis()
            );
            itemClaimStore.saveOrReplaceInMemory(updatedClaim);
            mutation = itemClaimStore.putMutation(updatedClaim);
        }
        transaction.onRollback(() -> itemClaimStore.saveOrReplaceInMemory(originalClaim));
        return mutation;
    }

    private void registerListingWriteRollback(MarketWriteTransaction transaction, ListingWriteHelper.ListingWriteResult writeResult) {
        transaction.onRollback(() -> listingWriteHelper.rollbackWriteInMemory(writeResult));
    }

    private int maxPerSlot(ItemStack itemStack) {
        return Math.max(1, itemStack.getMaxStackSize());
    }

    private MarketEventRecord buildItemClaimEvent(
            UUID ownerUuid,
            ResolvedItemDefinition resolved,
            int amount,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new MarketEventRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                MarketEventType.CLAIM_ITEM,
                null,
                null,
                null,
                ownerUuid,
                resolved.marketKey(),
                resolved.marketDisplayName(),
                resolved.categoryId(),
                resolved.marketDisplayName() + " x" + amount,
                null,
                amount,
                0L,
                0L,
                status,
                reason,
                null
        );
    }

    private MarketEventRecord buildMoneyClaimEvent(
            UUID ownerUuid,
            long amount,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new MarketEventRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                MarketEventType.CLAIM_MONEY,
                null,
                null,
                null,
                ownerUuid,
                null,
                "Earnings Claim",
                null,
                "Earnings x1",
                null,
                1,
                amount,
                amount,
                status,
                reason,
                null
        );
    }

    private MarketEventRecord buildListingEvent(
            Listing listing,
            UUID actorUuid,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new MarketEventRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                MarketEventType.LIST,
                listing.listingId().toString(),
                listing.sellerUuid(),
                null,
                actorUuid,
                listing.marketKey(),
                listing.marketDisplayName(),
                listing.categoryId(),
                listing.marketDisplayName() + " x" + listing.amount(),
                null,
                listing.amount(),
                listing.unitPrice() * listing.amount(),
                listing.unitPrice(),
                status,
                reason,
                null
        );
    }

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }
}
