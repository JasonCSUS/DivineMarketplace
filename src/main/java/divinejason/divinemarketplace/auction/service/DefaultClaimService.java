package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.ListingCreateFailureReason;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.persistence.BinaryItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.BinaryMoneyClaimStore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Concrete claim lifecycle implementation.
 *
 * Shared helper note:
 * - relist-from-claim now uses ListingWriteHelper instead of duplicating listing persistence rules
 * - item delivery now uses StorageItemDeliveryHelper instead of duplicating storage-contents logic
 */
public final class DefaultClaimService implements ClaimService {
    private final BinaryItemClaimStore itemClaimStore;
    private final BinaryMoneyClaimStore moneyClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final AdminHistoryService adminHistoryService;
    private final Economy economy;
    private final StorageItemDeliveryHelper storageItemDeliveryHelper;
    private final ListingPolicyResolver listingPolicyResolver;
    private final ListingWriteHelper listingWriteHelper;

    public DefaultClaimService(
            BinaryItemClaimStore itemClaimStore,
            BinaryMoneyClaimStore moneyClaimStore,
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
    public void claimOneChunk(Player player, UUID claimId) {
        requirePlayer(player);

        ItemClaimRecord claim = loadOwnedClaimOrThrow(player, claimId);
        int deliverAmount = Math.min(claim.amount(), maxPerSlot(claim.claimItemSnapshot()));

        if (!storageItemDeliveryHelper.canFitAmount(player.getInventory(), claim.claimItemSnapshot(), deliverAmount)) {
            throw new IllegalStateException("Inventory does not have enough free storage space for one safe claim chunk.");
        }

        storageItemDeliveryHelper.insertExactQuantity(player.getInventory(), claim.claimItemSnapshot(), deliverAmount);
        persistClaimAfterReduction(claim, deliverAmount);

        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizeSnapshotAmount(claim.claimItemSnapshot()));
        adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                claim.ownerUuid(),
                resolved,
                deliverAmount,
                System.currentTimeMillis(),
                "CLAIMED",
                "ONE_CHUNK"
        ));
    }

    @Override
    public void claimAsMuchAsFits(Player player, UUID claimId) {
        requirePlayer(player);

        ItemClaimRecord claim = loadOwnedClaimOrThrow(player, claimId);
        int deliverAmount = storageItemDeliveryHelper.maxInsertableAmount(player.getInventory(), claim.claimItemSnapshot(), claim.amount());

        if (deliverAmount <= 0) {
            throw new IllegalStateException("Inventory does not have enough free storage space to claim this item.");
        }

        storageItemDeliveryHelper.insertExactQuantity(player.getInventory(), claim.claimItemSnapshot(), deliverAmount);
        persistClaimAfterReduction(claim, deliverAmount);

        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizeSnapshotAmount(claim.claimItemSnapshot()));
        adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                claim.ownerUuid(),
                resolved,
                deliverAmount,
                System.currentTimeMillis(),
                "CLAIMED",
                "AS_MUCH_AS_FITS"
        ));
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

            persistClaimAfterReduction(claim, clampedQuantity);

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
            return ListingCreateResult.failure(exception.failureReason(), quantity, clampedQuantity, exception.getMessage());
        } catch (RuntimeException exception) {
            return ListingCreateResult.failure(ListingCreateFailureReason.INTERNAL_ERROR, quantity, clampedQuantity, exception.getMessage());
        }
    }

    @Override
    public void claimEarnings(Player player) {
        requirePlayer(player);

        long pendingAmount = moneyClaimStore.getBalanceOrZero(player.getUniqueId());
        if (pendingAmount <= 0L) {
            return;
        }

        double payoutAmount = pendingAmount / 100.0;
        EconomyResponse response = economy.depositPlayer(player, payoutAmount);
        if (!response.transactionSuccess()) {
            throw new IllegalStateException("Vault deposit failed: " + response.errorMessage);
        }

        moneyClaimStore.subtractFromBalance(player.getUniqueId(), pendingAmount);
        moneyClaimStore.deleteIfZero(player.getUniqueId());

        adminHistoryService.recordClaim(buildMoneyClaimAdminRecord(
                player.getUniqueId(),
                pendingAmount,
                System.currentTimeMillis(),
                "PAID_OUT",
                "CLAIM_EARNINGS"
        ));
    }

    private void requirePlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
    }

    private ItemClaimRecord loadOwnedClaimOrThrow(Player player, UUID claimId) {
        return itemClaimStore.findById(claimId, player.getUniqueId())
                .orElseThrow(() -> new IllegalStateException("Claim not found for player: " + claimId));
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
