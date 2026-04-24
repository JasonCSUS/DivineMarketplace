package divinejason.divinemarketplace.auction.model;

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
