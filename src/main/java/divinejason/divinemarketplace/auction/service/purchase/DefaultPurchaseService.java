package divinejason.divinemarketplace.auction.service.purchase;

/*
 * Layer : service
 * Owns  : purchase behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Implements purchase service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.model.MarketHistoryParticipation;
import divinejason.divinemarketplace.auction.model.PurchaseFailureReason;
import divinejason.divinemarketplace.auction.model.PurchaseResult;
import divinejason.divinemarketplace.auction.model.ResolvedItemDefinition;
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.claim.StorageItemDeliveryHelper;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import divinejason.divinemarketplace.auction.service.identity.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteItemClaimStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMoneyClaimStore;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Concrete listing purchase flow.
 *
 * Purchase delivery policy:
 * - bought items always move into the buyer's item-claim storage
 * - GUI claim screens deliver purchased items from claims in safe inventory-sized chunks
 * - direct inventory insertion is intentionally not part of purchase rollback
 *
 * Event policy:
 * - one BUY MarketEventRecord is written per purchase regardless of history participation
 * - itemSnapshot is set only when MarketHistoryParticipation.INCLUDED so the event
 *   appears in player-facing sale history; admin audit always sees the event
 */
public final class DefaultPurchaseService implements PurchaseService {
    private final Logger logger = Logger.getLogger(DefaultPurchaseService.class.getName());

    private final SQLiteListingStore listingStore;
    private final SQLiteItemClaimStore itemClaimStore;
    private final SQLiteMoneyClaimStore moneyClaimStore;
    private final MarketEventService marketEventService;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final ItemIdentityResolver itemIdentityResolver;
    private final CategoryService categoryService;
    private final Economy economy;

    public DefaultPurchaseService(
            SQLiteListingStore listingStore,
            SQLiteItemClaimStore itemClaimStore,
            SQLiteMoneyClaimStore moneyClaimStore,
            MarketEventService marketEventService,
            SQLiteWriteBehindQueue writeBehindQueue,
            ItemIdentityResolver itemIdentityResolver,
            CategoryService categoryService,
            StorageItemDeliveryHelper storageItemDeliveryHelper,
            Economy economy
    ) {
        this.listingStore = Objects.requireNonNull(listingStore, "listingStore");
        this.itemClaimStore = Objects.requireNonNull(itemClaimStore, "itemClaimStore");
        this.moneyClaimStore = Objects.requireNonNull(moneyClaimStore, "moneyClaimStore");
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
        this.writeBehindQueue = Objects.requireNonNull(writeBehindQueue, "writeBehindQueue");
        this.itemIdentityResolver = Objects.requireNonNull(itemIdentityResolver, "itemIdentityResolver");
        this.categoryService = Objects.requireNonNull(categoryService, "categoryService");
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
            SQLiteWriteBatch.Builder writeBatch = SQLiteWriteBatch.builder("purchase listing " + originalListing.listingId());

            if (updatedListing == null) {
                listingStore.deleteInMemory(originalListing.listingId());
                writeBatch.add(listingStore.deleteMutation(originalListing.listingId()));
            } else {
                listingStore.saveOrReplaceInMemory(updatedListing);
                writeBatch.add(listingStore.putMutation(updatedListing));
            }
            transaction.onRollback(() -> listingStore.saveOrReplaceInMemory(originalListing));

            long previousSellerBalance = moneyClaimStore.getBalanceOrZero(originalListing.sellerUuid());
            long updatedSellerBalance = moneyClaimStore.addToBalanceInMemory(originalListing.sellerUuid(), totalPrice);
            writeBatch.add(moneyClaimStore.putOrDeleteMutation(originalListing.sellerUuid(), updatedSellerBalance));
            transaction.onRollback(() -> moneyClaimStore.setBalanceInMemory(originalListing.sellerUuid(), previousSellerBalance));

            ItemClaimRecord savedBuyerClaim = createOrMergeBuyerItemClaimWithRollback(transaction, buyer.getUniqueId(), normalizedSnapshot, quantity, nowEpochMillis);
            writeBatch.add(itemClaimStore.putMutation(savedBuyerClaim));

            MarketEventRecord buyEvent = buildBuyEvent(
                    originalListing, buyer.getUniqueId(), quantity, totalPrice, nowEpochMillis, resolved);
            marketEventService.appendInMemory(buyEvent);
            writeBatch.add(marketEventService.putMutation(buyEvent));
            transaction.onRollback(() -> marketEventService.deleteInMemory(buyEvent.eventId()));

            writeBehindQueue.enqueue(writeBatch.build());
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

    private ItemClaimRecord createOrMergeBuyerItemClaimWithRollback(MarketWriteTransaction transaction, UUID ownerUuid, ItemStack normalizedSnapshot, int amount, long createdAtEpochMillis) {
        ItemClaimRecord previousMergeTarget = findExistingSimilarClaim(ownerUuid, normalizedSnapshot);
        ItemClaimRecord incomingClaim = new ItemClaimRecord(
                UUID.randomUUID(),
                ownerUuid,
                normalizedSnapshot.clone(),
                amount,
                createdAtEpochMillis
        );
        ItemClaimRecord savedClaim = itemClaimStore.mergeOrCreateInMemory(incomingClaim);
        transaction.onRollback(() -> {
            if (previousMergeTarget == null) {
                itemClaimStore.deleteInMemory(savedClaim.claimId(), ownerUuid);
            } else {
                itemClaimStore.saveOrReplaceInMemory(previousMergeTarget);
            }
        });
        return savedClaim;
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

    private MarketEventRecord buildBuyEvent(
            Listing listing,
            UUID buyerUuid,
            int quantity,
            long totalPrice,
            long timestampEpochMillis,
            ResolvedItemDefinition resolved
    ) {
        boolean playerHistory = resolved.marketHistoryParticipation() == MarketHistoryParticipation.INCLUDED;
        return new MarketEventRecord(
                UUID.randomUUID().toString(),
                timestampEpochMillis,
                MarketEventType.BUY,
                listing.listingId().toString(),
                listing.sellerUuid(),
                buyerUuid,
                buyerUuid,
                listing.marketKey(),
                listing.marketDisplayName(),
                listing.categoryId(),
                listing.marketDisplayName() + " x" + quantity,
                playerHistory ? listing.listedItemSnapshot() : null,
                quantity,
                totalPrice,
                listing.unitPrice(),
                "PURCHASED",
                "BUY_TO_BUYER_CLAIM",
                playerHistory ? resolved.marketTrainingParticipation() : null
        );
    }

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }
}
