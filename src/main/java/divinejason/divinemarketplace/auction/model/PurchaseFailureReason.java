package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates purchase failure reason values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Stable purchase failure reasons for command/menu UX.
 */
public enum PurchaseFailureReason {
    INVALID_REQUEST,
    LISTING_NOT_FOUND,
    SELF_PURCHASE_BLOCKED,
    QUANTITY_UNAVAILABLE,
    INSUFFICIENT_FUNDS,
    ECONOMY_WITHDRAW_FAILED,
    CLAIM_SLOT_UNAVAILABLE,
    INTERNAL_ERROR
}
