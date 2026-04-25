package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles item-claim redemption, claim-to-listing relist flow, and money-claim payout.
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
     * Relist directly from an item claim without sending the item back through inventory first.
     *
     * Locked intent:
     * - used when a player cancels and wants to relist at a new price
     * - should support partial relist quantity
     * - should clamp requested quantity to the claim amount
     * - should create/merge a fresh listing using the claim snapshot as the item source
     * - should decrement or delete the claim only after successful listing creation
     * - should return a ListingCreateResult so command/menu code can notify the player cleanly
     *
     * PSEUDOCODE:
     * - load claim by owner + claim id
     * - verify ownership
     * - clamp relist quantity to claim amount
     * - attempt listing creation using claim snapshot instead of live inventory
     * - if listing succeeds:
     *   - decrement claim amount by actualQuantity
     *   - delete claim if amount reaches 0
     *   - write admin claim/listing history as needed
     * - return structured success/failure result
     */
    ListingCreateResult relistClaim(Player player, UUID claimId, int quantity, long unitPrice);

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
