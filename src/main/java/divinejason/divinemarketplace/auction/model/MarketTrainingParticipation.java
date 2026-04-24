package divinejason.divinemarketplace.auction.model;

/**
 * Whether an item/sale should contribute to recommendation training.
 *
 * This is separate from:
 * - mandatory admin transaction history
 * - player-facing market history
 */
public enum MarketTrainingParticipation {
    INCLUDED,
    EXCLUDED
}
