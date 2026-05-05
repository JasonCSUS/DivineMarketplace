package divinejason.divinemarketplace.auction.service;


/*
 * File role: Implements claim service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.model.ClaimItemResult;
import divinejason.divinemarketplace.auction.model.ClaimMoneyResult;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMoneyClaimStore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Concrete claim lifecycle implementation.
 *
 * Shared helper note:
 * - relist-from-claim now uses ListingWriteHelper instead of duplicating listing persistence rules
 * - item delivery now uses StorageItemDeliveryHelper instead of duplicating storage-contents logic
 */
public final class DefaultClaimService implements ClaimService {
    private final Logger logger = Logger.getLogger(DefaultClaimService.class.getName());
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final AdminHistoryService adminHistoryService;
    private final Economy economy;
    private final StorageItemDeliveryHelper storageItemDeliveryHelper;
    private final ListingPolicyResolver listingPolicyResolver;
    private final ListingWriteHelper listingWriteHelper;

    public DefaultClaimService(
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            ItemIdentityResolver itemIdentityResolver,
            AdminHistoryService adminHistoryService,
            Economy economy,
            StorageItemDeliveryHelper storageItemDeliveryHelper,
            ListingPolicyResolver listingPolicyResolver,
            ListingWriteHelper listingWriteHelper
    ) {
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.itemIdentityResolver = Objects.requireNonNull(itemIdentityResolver, "itemIdentityResolver");
        this.adminHistoryService = Objects.requireNonNull(adminHistoryService, "adminHistoryService");
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
            ListingWriteHelper.ListingWriteResult writeResult = listingWriteHelper.createOrMerge(
                    player.getUniqueId(),
                    normalizedSnapshot,
                    clampedQuantity,
                    unitPrice,
                    resolved,
                    listingPolicy,
                    nowEpochMillis
            );
            registerListingWriteRollback(transaction, writeResult);

            persistClaimAfterReductionWithRollback(transaction, claim, clampedQuantity);

            adminHistoryService.recordListing(buildListingAdminRecord(
                    writeResult.listing(),
                    player.getUniqueId(),
                    nowEpochMillis,
                    writeResult.mergedIntoExisting() ? "MERGED" : "CREATED",
                    "CLAIM_RELIST"
            ));

            adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                    player.getUniqueId(),
                    resolved,
                    clampedQuantity,
                    nowEpochMillis,
                    "RELISTED",
                    "CLAIM_TO_LISTING"
            ));

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
            moneyClaimStore.subtractFromBalance(player.getUniqueId(), pendingAmount);
            moneyClaimStore.deleteIfZero(player.getUniqueId());
            transaction.onRollback(() -> moneyClaimStore.addToBalance(player.getUniqueId(), pendingAmount));

            EconomyResponse response = economy.depositPlayer(player, payoutAmount);
            if (!response.transactionSuccess()) {
                throw new IllegalStateException("Vault deposit failed: " + response.errorMessage);
            }
            transaction.onRollback(() -> economy.withdrawPlayer(player, payoutAmount));

            adminHistoryService.recordClaim(buildMoneyClaimAdminRecord(
                    player.getUniqueId(),
                    pendingAmount,
                    System.currentTimeMillis(),
                    "PAID_OUT",
                    "CLAIM_EARNINGS"
            ));

            transaction.commit();
            return ClaimMoneyResult.success(pendingAmount);
        } catch (RuntimeException exception) {
            transaction.rollbackQuietly();
            return ClaimMoneyResult.failure(pendingAmount, exception.getMessage());
        }
    }

    /**
     * Performs the durable reduction, admin-history write, and exact inventory
     * insertion for item claims. The claim store is updated before inventory
     * insertion so rollback can restore the original durable claim if a subsequent
     * step fails.
     */
    private ClaimItemResult deliverItemClaim(Player player, ItemClaimRecord claim, int deliverAmount, String reason) {
        MarketWriteTransaction transaction = new MarketWriteTransaction(logger);
        try {
            persistClaimAfterReductionWithRollback(transaction, claim, deliverAmount);

            ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizeSnapshotAmount(claim.claimItemSnapshot()));
            adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                    claim.ownerUuid(),
                    resolved,
                    deliverAmount,
                    System.currentTimeMillis(),
                    "CLAIMED",
                    reason
            ));

            storageItemDeliveryHelper.insertExactQuantity(player.getInventory(), claim.claimItemSnapshot(), deliverAmount);
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

    private void persistClaimAfterReduction(ItemClaimRecord originalClaim, int deliveredAmount) {
        int remainingAmount = originalClaim.amount() - deliveredAmount;
        if (remainingAmount <= 0) {
            itemClaimStore.delete(originalClaim.claimId(), originalClaim.ownerUuid());
            return;
        }

        itemClaimStore.saveOrReplace(new ItemClaimRecord(
                originalClaim.claimId(),
                originalClaim.ownerUuid(),
                originalClaim.claimItemSnapshot(),
                remainingAmount,
                originalClaim.createdAtEpochMillis()
        ));
    }

    private void persistClaimAfterReductionWithRollback(MarketWriteTransaction transaction, ItemClaimRecord originalClaim, int deliveredAmount) {
        persistClaimAfterReduction(originalClaim, deliveredAmount);
        transaction.onRollback(() -> itemClaimStore.saveOrReplace(originalClaim));
    }

    private void registerListingWriteRollback(MarketWriteTransaction transaction, ListingWriteHelper.ListingWriteResult writeResult) {
        transaction.onRollback(() -> listingWriteHelper.rollbackWrite(writeResult));
    }

    private int maxPerSlot(ItemStack itemStack) {
        return Math.max(1, itemStack.getMaxStackSize());
    }

    private AdminTransactionRecord buildItemClaimAdminRecord(
            UUID ownerUuid,
            ResolvedItemDefinition resolved,
            int amount,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new AdminTransactionRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                AdminTransactionType.CLAIM_ITEM,
                null,
                null,
                null,
                ownerUuid,
                resolved.marketKey(),
                resolved.marketDisplayName(),
                resolved.categoryId(),
                resolved.marketDisplayName() + " x" + amount,
                amount,
                0L,
                0L,
                status,
                reason
        );
    }

    private AdminTransactionRecord buildMoneyClaimAdminRecord(
            UUID ownerUuid,
            long amount,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new AdminTransactionRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                AdminTransactionType.CLAIM_MONEY,
                null,
                null,
                null,
                ownerUuid,
                null,
                "Earnings Claim",
                null,
                "Earnings x1",
                1,
                amount,
                amount,
                status,
                reason
        );
    }

    private AdminTransactionRecord buildListingAdminRecord(
            Listing listing,
            UUID actorUuid,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new AdminTransactionRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                AdminTransactionType.LIST,
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

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }
}
