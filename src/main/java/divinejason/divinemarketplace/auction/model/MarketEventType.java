package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates canonical market event type values used by market_events persistence, services, commands, and GUI rendering.
 */
/**
 * Canonical event type for all market_events entries.
 *
 * Mirrors AdminTransactionType exactly for this patch so projection from
 * MarketEventRecord to AdminTransactionRecord is a simple name lookup.
 * AdminTransactionType is kept as a read-only view alias until it can be removed.
 */
public enum MarketEventType {
    LIST,
    BUY,
    CANCEL,
    EXPIRE,
    CLAIM_ITEM,
    CLAIM_MONEY,
    CONFISCATE,
    ADMIN_ACTION
}
