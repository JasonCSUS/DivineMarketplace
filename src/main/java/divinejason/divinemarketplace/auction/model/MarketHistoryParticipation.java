package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates market history participation values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Whether an item/sale should appear in player-facing market history.
 *
 * This is separate from:
 * - mandatory admin transaction history
 * - market training participation
 */
public enum MarketHistoryParticipation {
    INCLUDED,
    EXCLUDED
}
