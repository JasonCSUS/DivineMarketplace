package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates listing create failure reason values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Expected user-facing or command-facing reasons a listing creation request failed.
 *
 * These are intentionally stable enough for command/menu code to map into:
 * - player-friendly messages
 * - debug logs
 * - optional telemetry or admin diagnostics
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
