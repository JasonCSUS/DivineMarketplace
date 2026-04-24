package divinejason.divinemarketplace.auction.service;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles item-claim redemption and money-claim payout.
 */
public interface ClaimService {

    /**
     * Redeem one safe stack-sized chunk from an item claim.
     *
     * PSEUDOCODE:
     * - load claim
     * - determine item max stack size from claimItemSnapshot
     * - deliverAmount = min(claim.amount, itemMaxStackSize)
     * - verify player inventory can accept the chunk
     * - give the chunk
     * - decrement claim amount
     * - remove claim if amount reaches 0
     * - write admin claim history
     */
    void claimOneChunk(Player player, UUID claimId);

    /**
     * Redeem as much of one claim as safely fits in inventory.
     *
     * PSEUDOCODE:
     * - repeatedly attempt safe chunks until inventory would overflow
     * - stop without losing remaining claim amount
     */
    void claimAsMuchAsFits(Player player, UUID claimId);

    /**
     * Payout accumulated seller earnings.
     *
     * PSEUDOCODE:
     * - read pending balance or zero
     * - if zero, do nothing / message user
     * - attempt Vault deposit using decimal boundary conversion
     * - on success subtract the exact claimed amount from the stored balance
     * - delete zero-balance record
     * - write admin claim/payout history
     */
    void claimEarnings(Player player);
}
