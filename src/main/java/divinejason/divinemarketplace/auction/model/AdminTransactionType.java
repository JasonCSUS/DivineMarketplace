package divinejason.divinemarketplace.auction.model;

/**
 * Broad admin-audit event types.
 */
public enum AdminTransactionType {
    LIST,
    BUY,
    CANCEL,
    EXPIRE,
    CLAIM_ITEM,
    CLAIM_MONEY,
    CONFISCATE,
    ADMIN_ACTION
}
