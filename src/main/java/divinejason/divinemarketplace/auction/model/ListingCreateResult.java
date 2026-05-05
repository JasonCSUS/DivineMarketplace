package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable listing create result data between marketplace services, persistence stores, commands, and GUI rendering.
 */
import java.util.UUID;

/**
 * Result object for listing creation/merge attempts.
 *
 * Why this exists:
 * - lets command/menu code notify the player cleanly
 * - lets callers inspect failure reason without parsing exception messages
 * - keeps debugging information available without forcing a crash path for
 *   every user mistake
 */
public record ListingCreateResult(
        boolean success,
        UUID listingId,
        boolean mergedIntoExisting,
        int requestedQuantity,
        int actualQuantity,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        ListingCreateFailureReason failureReason,
        String debugMessage
) {
    public static ListingCreateResult success(
            UUID listingId,
            boolean mergedIntoExisting,
            int requestedQuantity,
            int actualQuantity,
            String marketKey,
            String marketDisplayName,
            String categoryId
    ) {
        return new ListingCreateResult(
                true,
                listingId,
                mergedIntoExisting,
                requestedQuantity,
                actualQuantity,
                marketKey,
                marketDisplayName,
                categoryId,
                null,
                null
        );
    }

    public static ListingCreateResult failure(
            ListingCreateFailureReason failureReason,
            int requestedQuantity,
            int actualQuantity,
            String debugMessage
    ) {
        return new ListingCreateResult(
                false,
                null,
                false,
                requestedQuantity,
                actualQuantity,
                null,
                null,
                null,
                failureReason,
                debugMessage
        );
    }
}
