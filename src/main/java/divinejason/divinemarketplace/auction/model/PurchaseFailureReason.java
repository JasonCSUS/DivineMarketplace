package divinejason.divinemarketplace.auction.model;

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
    INTERNAL_ERROR
}
