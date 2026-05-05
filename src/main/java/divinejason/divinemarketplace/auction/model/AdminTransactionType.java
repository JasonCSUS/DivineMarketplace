package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates admin transaction type values used by marketplace services, persistence, commands, and GUI rendering.
 */
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
