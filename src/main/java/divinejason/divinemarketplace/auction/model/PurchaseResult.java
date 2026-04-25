package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

/**
 * Structured purchase result for player feedback and debugging.
 */
public record PurchaseResult(
        boolean success,
        UUID listingId,
        int quantityPurchased,
        long totalPrice,
        int remainingListingAmount,
        boolean deliveredDirectly,
        boolean createdItemClaim,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        PurchaseFailureReason failureReason,
        String debugMessage
) {
    public static PurchaseResult success(
            UUID listingId,
            int quantityPurchased,
            long totalPrice,
            int remainingListingAmount,
            boolean deliveredDirectly,
            boolean createdItemClaim,
            String marketKey,
            String marketDisplayName,
            String categoryId
    ) {
        return new PurchaseResult(
                true,
                listingId,
                quantityPurchased,
                totalPrice,
                remainingListingAmount,
                deliveredDirectly,
                createdItemClaim,
                marketKey,
                marketDisplayName,
                categoryId,
                null,
                null
        );
    }

    public static PurchaseResult failure(
            PurchaseFailureReason failureReason,
            UUID listingId,
            int quantityPurchased,
            long totalPrice,
            String debugMessage
    ) {
        return new PurchaseResult(
                false,
                listingId,
                quantityPurchased,
                totalPrice,
                0,
                false,
                false,
                null,
                null,
                null,
                failureReason,
                debugMessage
        );
    }
}
