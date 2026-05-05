package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates market training participation values used by marketplace services, persistence, commands, and GUI rendering.
 */
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
