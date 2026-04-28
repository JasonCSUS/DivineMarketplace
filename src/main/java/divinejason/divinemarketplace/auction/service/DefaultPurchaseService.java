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
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Concrete listing purchase flow.
 */
public final class DefaultPurchaseService implements PurchaseService {
    private final SQLiteListingStore listingStore;
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final ItemIdentityResolver itemIdentityResolver;
    private final AdminHistoryService adminHistoryService;
    private final CategoryService categoryService;
    private final MarketAnalyticsService marketAnalyticsService;
    private final StorageItemDeliveryHelper storageItemDeliveryHelper;
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
        this.storageItemDeliveryHelper = Objects.requireNonNull(storageItemDeliveryHelper, "storageItemDeliveryHelper");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    @Override
    public PurchaseResult purchase(Player buyer, UUID listingId, int quantity) {
        if (buyer == null || listingId == null || quantity <= 0) {
            return PurchaseResult.failure(PurchaseFailureReason.INVALID_REQUEST, listingId, 0, 0L, "buyer/listingId invalid or quantity <= 0");
        }

        Listing listing = listingStore.findById(listingId).orElse(null);
        if (listing == null) {
            return PurchaseResult.failure(PurchaseFailureReason.LISTING_NOT_FOUND, listingId, 0, 0L, "Listing no longer exists.");
        }

        if (buyer.getUniqueId().equals(listing.sellerUuid())) {
            return PurchaseResult.failure(PurchaseFailureReason.SELF_PURCHASE_BLOCKED, listingId, 0, 0L, "Seller cannot buy their own listing.");
        }

        if (quantity > listing.amount()) {
            return PurchaseResult.failure(PurchaseFailureReason.QUANTITY_UNAVAILABLE, listingId, 0, 0L, "Requested quantity exceeds live listing amount.");
        }

        long totalPrice;
        try {
            totalPrice = Math.multiplyExact(listing.unitPrice(), (long) quantity);
        } catch (ArithmeticException exception) {
            return PurchaseResult.failure(PurchaseFailureReason.INTERNAL_ERROR, listingId, 0, 0L, "Total price overflow.");
        }

        double totalPriceDecimal = totalPrice / 100.0;
        if (!economy.has(buyer, totalPriceDecimal)) {
            return PurchaseResult.failure(PurchaseFailureReason.INSUFFICIENT_FUNDS, listingId, 0, totalPrice, "Buyer does not have enough money.");
        }

        ResolvedItemDefinition resolved = itemIdentityResolver.resolve(normalizeSnapshotAmount(listing.listedItemSnapshot()));

        EconomyResponse withdraw = economy.withdrawPlayer(buyer, totalPriceDecimal);
        if (!withdraw.transactionSuccess()) {
            return PurchaseResult.failure(PurchaseFailureReason.ECONOMY_WITHDRAW_FAILED, listingId, 0, totalPrice, withdraw.errorMessage);
        }

        Listing originalListing = listing;
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

        try {
            if (updatedListing == null) {
                listingStore.delete(originalListing.listingId());
            } else {
                listingStore.saveOrReplace(updatedListing);
            }

            moneyClaimStore.addToBalance(originalListing.sellerUuid(), totalPrice);

            if (resolved.marketHistoryParticipation() == MarketHistoryParticipation.INCLUDED) {
                marketAnalyticsService.recordSale(new SaleRecord(
                        originalListing.marketKey(),
                        originalListing.marketDisplayName(),
                        originalListing.listedItemSnapshot(),
                        quantity,
                        originalListing.unitPrice(),
                        System.currentTimeMillis(),
                        resolved.marketTrainingParticipation()
                ));
            }

            adminHistoryService.recordSale(buildSaleAdminRecord(
                    originalListing,
                    buyer.getUniqueId(),
                    quantity,
                    totalPrice,
                    System.currentTimeMillis(),
                    "PURCHASED",
                    "DIRECT_BUY"
            ));
        } catch (RuntimeException exception) {
            rollbackPurchasePersistence(buyer, totalPriceDecimal, totalPrice, originalListing);
            return PurchaseResult.failure(PurchaseFailureReason.INTERNAL_ERROR, listingId, 0, totalPrice, exception.getMessage());
        }

        boolean deliveredDirectly;
        boolean createdItemClaim;

        if (storageItemDeliveryHelper.canFitAmount(buyer.getInventory(), originalListing.listedItemSnapshot(), quantity)) {
            try {
                storageItemDeliveryHelper.insertExactQuantity(buyer.getInventory(), originalListing.listedItemSnapshot(), quantity);
                deliveredDirectly = true;
                createdItemClaim = false;
            } catch (RuntimeException exception) {
                createOrMergeBuyerItemClaim(buyer.getUniqueId(), originalListing.listedItemSnapshot(), quantity, System.currentTimeMillis());
                adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                        buyer.getUniqueId(),
                        resolved,
                        quantity,
                        System.currentTimeMillis(),
                        "CREATED",
                        "PURCHASE_DELIVERY_FALLBACK"
                ));
                deliveredDirectly = false;
                createdItemClaim = true;
            }
        } else {
            createOrMergeBuyerItemClaim(buyer.getUniqueId(), originalListing.listedItemSnapshot(), quantity, System.currentTimeMillis());
            adminHistoryService.recordClaim(buildItemClaimAdminRecord(
                    buyer.getUniqueId(),
                    resolved,
                    quantity,
                    System.currentTimeMillis(),
                    "CREATED",
                    "PURCHASE_OVERFLOW_CLAIM"
            ));
            deliveredDirectly = false;
            createdItemClaim = true;
        }

        categoryService.refreshIndexesFor(originalListing.marketKey(), originalListing.categoryId());

        return PurchaseResult.success(
                originalListing.listingId(),
                quantity,
                totalPrice,
                Math.max(0, remainingAmount),
                deliveredDirectly,
                createdItemClaim,
                originalListing.marketKey(),
                originalListing.marketDisplayName(),
                originalListing.categoryId()
        );
    }

    private void rollbackPurchasePersistence(Player buyer, double totalPriceDecimal, long totalPrice, Listing originalListing) {
        EconomyResponse refund = economy.depositPlayer(buyer, totalPriceDecimal);
        if (!refund.transactionSuccess()) {
            throw new IllegalStateException("Purchase rollback failed to refund buyer after downstream failure: " + refund.errorMessage);
        }

        listingStore.saveOrReplace(originalListing);
        moneyClaimStore.subtractFromBalance(originalListing.sellerUuid(), totalPrice);
        moneyClaimStore.deleteIfZero(originalListing.sellerUuid());
    }

    private void createOrMergeBuyerItemClaim(UUID ownerUuid, ItemStack baseSnapshot, int amount, long createdAtEpochMillis) {
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
