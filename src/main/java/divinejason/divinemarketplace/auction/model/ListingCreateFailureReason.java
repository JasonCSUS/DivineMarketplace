package divinejason.divinemarketplace.auction.model;

/**
 * Expected user-facing or command-facing reasons a listing creation request failed.
 *
 * These are intentionally stable enough for command/menu code to map into:
 * - player-friendly messages
 * - debug logs
 * - telemetry later if desired
 */
public enum ListingCreateFailureReason {
    INVALID_REQUEST,
    MAIN_HAND_EMPTY,
    MAIN_HAND_ITEM_MISMATCH,
    MAIN_HAND_STACK_CHANGED,
    ITEM_IDENTITY_UNRESOLVED,
    ACTIVE_LISTING_LIMIT_REACHED,
    CLAIM_NOT_FOUND,
    CLAIM_EMPTY,
    INTERNAL_ERROR
}
