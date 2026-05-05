package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.MarketHistoryParticipation;
import divinejason.divinemarketplace.auction.model.PurchaseFailureReason;
import divinejason.divinemarketplace.auction.model.PurchaseResult;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.config.ConfigService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Concrete listing purchase flow.
 *
 * Purchase delivery policy:
 * - bought items always move into the buyer's item-claim storage
 * - GUI claim screens deliver items later, in safe inventory-sized chunks
 * - direct inventory insertion is intentionally not part of purchase rollback
 */
public final class DefaultPurchaseService implements PurchaseService {
    private final Logger logger = Logger.getLogger(DefaultPurchaseService.class.getName());

    private final SQLiteListingStore listingStore;
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final AdminHistoryService adminHistoryService;
    private final CategoryService categoryService;
    private final MarketAnalyticsService marketAnalyticsService;
    private final Economy economy;

    public DefaultPurchaseService(
            SQLiteListingStore listingStore,
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            ItemIdentityResolver itemIdentityResolver,
            AdminHistoryService adminHistoryService,
            CategoryService categoryService,
            MarketAnalyticsService marketAnalyticsService,
            StorageItemDeliveryHelper storageItemDeliveryHelper,
            Economy economy
    ) {
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.itemIdentityResolver = Objects.requireNonNull(itemIdentityResolver, "itemIdentityResolver");
        this.adminHistoryService = Objects.requireNonNull(adminHistoryService, "adminHistoryService");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
        this.marketAnalyticsService = Objects.requireNonNull(marketAnalyticsService, "marketAnalyticsService");
        Objects.requireNonNull(storageItemDeliveryHelper, "storageItemDeliveryHelper");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public PurchaseResult purchase(Player buyer, UUID listingId, int quantity) {
        if (buyer == null || listingId == null || quantity <= 0) {
            return PurchaseResult.failure(PurchaseFailureReason.INVALID_REQUEST, listingId, 0, 0L, "buyer/listingId invalid or quantity <= 0");
        }

        Listing originalListing = listingStore.findById(listingId).orElse(null);
        if (originalListing == null) {
            return PurchaseResult.failure(PurchaseFailureReason.LISTING_NOT_FOUND, listingId, 0, 0L, "Listing no longer exists.");
        }

        if (buyer.getUniqueId().equals(originalListing.sellerUuid())) {
            return PurchaseResult.failure(PurchaseFailureReason.SELF_PURCHASE_BLOCKED, listingId, 0, 0L, "Seller cannot buy their own listing.");
        }

        if (quantity > originalListing.amount()) {
            return PurchaseResult.failure(PurchaseFailureReason.QUANTITY_UNAVAILABLE, listingId, 0, 0L, "Requested quantity exceeds live listing amount.");
        }

        long totalPrice;
        try {
            totalPrice = Math.multiplyExact(originalListing.unitPrice(), (long) quantity);
        } catch (ArithmeticException exception) {
            return PurchaseResult.failure(PurchaseFailureReason.INTERNAL_ERROR, listingId, 0, 0L, "Total price overflow.");
        }

        ItemStack normalizedSnapshot = normalizeSnapshotAmount(originalListing.listedItemSnapshot());
        if (!canCreateOrMergeBuyerClaim(buyer.getUniqueId(), normalizedSnapshot, quantity)) {
            return PurchaseResult.failure(PurchaseFailureReason.CLAIM_SLOT_UNAVAILABLE, listingId, 0, totalPrice, "Your item-claim storage is full. Empty room in /market claim, close the market window, and try again.");
        }

        double totalPriceDecimal = totalPrice / 100.0;
        if (!economy.has(buyer, totalPriceDecimal)) {
            return PurchaseResult.failure(PurchaseFailureReason.INSUFFICIENT_FUNDS, listingId, 0, totalPrice, "Buyer does not have enough money.");
        }

        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizedSnapshot.clone());
        MarketWriteTransaction transaction = new MarketWriteTransaction(logger);

        EconomyResponse withdraw = economy.withdrawPlayer(buyer, totalPriceDecimal);
        if (!withdraw.transactionSuccess()) {
            return PurchaseResult.failure(PurchaseFailureReason.ECONOMY_WITHDRAW_FAILED, listingId, 0, totalPrice, withdraw.errorMessage);
        }
        transaction.onRollback(() -> economy.depositPlayer(buyer, totalPriceDecimal));

        int remainingAmount = originalListing.amount() - quantity;
        Listing updatedListing = remainingAmount > 0
                ? new Listing(
                        originalListing.listingId(),
                        originalListing.sellerUuid(),
                        originalListing.listedItemSnapshot(),
                        remainingAmount,
                        originalListing.marketKey(),
                        originalListing.marketDisplayName(),
                        originalListing.categoryId(),
                        originalListing.unitPrice(),
                        originalListing.listedAtEpochMillis(),
                        originalListing.listingDurationMillis()
                )
                : null;

        long nowEpochMillis = System.currentTimeMillis();

        try {
            if (updatedListing == null) {
                listingStore.delete(originalListing.listingId());
            } else {
                listingStore.saveOrReplace(updatedListing);
            }
            transaction.onRollback(() -> listingStore.saveOrReplace(originalListing));

            moneyClaimStore.addToBalance(originalListing.sellerUuid(), totalPrice);
            transaction.onRollback(() -> {
                moneyClaimStore.subtractFromBalance(originalListing.sellerUuid(), totalPrice);
                moneyClaimStore.deleteIfZero(originalListing.sellerUuid());
            });

            createOrMergeBuyerItemClaimWithRollback(transaction, buyer.getUniqueId(), normalizedSnapshot, quantity, nowEpochMillis);

            if (resolved.marketHistoryParticipation() == MarketHistoryParticipation.INCLUDED) {
                marketAnalyticsService.recordSale(new SaleRecord(
                        originalListing.marketKey(),
                        originalListing.marketDisplayName(),
                        originalListing.listedItemSnapshot(),
                        quantity,
                        originalListing.unitPrice(),
                        nowEpochMillis,
                        resolved.marketTrainingParticipation()
                ));
            }

            adminHistoryService.recordSale(buildSaleAdminRecord(
                    originalListing,
                    buyer.getUniqueId(),
                    quantity,
                    totalPrice,
                    nowEpochMillis,
                    "PURCHASED",
                    "BUY_TO_BUYER_CLAIM"
            ));

            adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                    buyer.getUniqueId(),
                    resolved,
                    quantity,
                    nowEpochMillis,
                    "CREATED",
                    "PURCHASE_BUYER_CLAIM"
            ));

            transaction.commit();
        } catch (RuntimeException exception) {
            transaction.rollbackQuietly();
            return PurchaseResult.failure(PurchaseFailureReason.INTERNAL_ERROR, listingId, 0, totalPrice, exception.getMessage());
        }

        categoryService.refreshIndexesFor(originalListing.marketKey(), originalListing.categoryId());

        return PurchaseResult.success(
                originalListing.listingId(),
                quantity,
                totalPrice,
                Math.max(0, remainingAmount),
                false,
                true,
                originalListing.marketKey(),
                originalListing.marketDisplayName(),
                originalListing.categoryId()
        );
    }

    private boolean canCreateOrMergeBuyerClaim(UUID ownerUuid, ItemStack normalizedSnapshot, int amount) {
        ItemClaimRecord mergeTarget = findExistingSimilarClaim(ownerUuid, normalizedSnapshot);
        if (mergeTarget != null) {
            long mergedAmount = (long) mergeTarget.amount() + amount;
            return mergedAmount <= Integer.MAX_VALUE;
        }

        int maxActiveClaims = ConfigService.get().itemClaimMaxActiveClaims();
        return itemClaimStore.countByOwner(ownerUuid) < maxActiveClaims;
    }

    private void createOrMergeBuyerItemClaimWithRollback(MarketWriteTransaction transaction, UUID ownerUuid, ItemStack normalizedSnapshot, int amount, long createdAtEpochMillis) {
        ItemClaimRecord previousMergeTarget = findExistingSimilarClaim(ownerUuid, normalizedSnapshot);
        ItemClaimRecord incomingClaim = new ItemClaimRecord(
                UUID.randomUUID(),
                ownerUuid,
                normalizedSnapshot.clone(),
                amount,
                createdAtEpochMillis
        );
        ItemClaimRecord savedClaim = itemClaimStore.mergeOrCreate(incomingClaim);
        transaction.onRollback(() -> {
            if (previousMergeTarget == null) {
                itemClaimStore.delete(savedClaim.claimId(), ownerUuid);
            } else {
                itemClaimStore.saveOrReplace(previousMergeTarget);
            }
        });
    }

    private ItemClaimRecord findExistingSimilarClaim(UUID ownerUuid, ItemStack normalizedSnapshot) {
        for (ItemClaimRecord claim : itemClaimStore.findByOwner(ownerUuid, 0, Integer.MAX_VALUE)) {
            if (itemsSimilarIgnoringAmount(claim.claimItemSnapshot(), normalizedSnapshot)) {
                return claim;
            }
        }
        return null;
    }

    private boolean itemsSimilarIgnoringAmount(ItemStack left, ItemStack right) {
        ItemStack leftCopy = normalizeSnapshotAmount(left);
        ItemStack rightCopy = normalizeSnapshotAmount(right);
        return leftCopy.isSimilar(rightCopy);
    }

    private AdminTransactionRecord buildSaleAdminRecord(
            Listing listing,
            UUID buyerUuid,
            int quantity,
            long totalPrice,
            long timestampEpochMillis,
            String status,
            String reason
    ) {
        return new AdminTransactionRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                AdminTransactionType.BUY,
                listing.listingId().toString(),
                listing.sellerUuid(),
                buyerUuid,
                buyerUuid,
                listing.marketKey(),
                listing.marketDisplayName(),
                listing.categoryId(),
                listing.marketDisplayName() + " x" + quantity,
                quantity,
                totalPrice,
                listing.unitPrice(),
                status,
                reason
        );
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

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }
}
