package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.ClaimItemResult;
import divinejason.divinemarketplace.auction.model.ClaimMoneyResult;
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles item-claim redemption, claim-to-listing relist flow, and money-claim payout.
 */
public interface ClaimService {

    /**
     * Redeems one safe stack-sized chunk from an owned item claim.
     *
     * Implementations must verify ownership, verify inventory capacity, decrement
     * the durable claim only when delivery succeeds, and record admin claim history.
     */
    ClaimItemResult claimOneChunk(Player player, UUID claimId);

    /**
     * Redeems as much of one owned claim as safely fits in the player's inventory.
     *
     * Remaining quantity must stay in durable claim storage when the full claim
     * cannot fit.
     */
    ClaimItemResult claimAsMuchAsFits(Player player, UUID claimId);

    /**
     * Relists item quantity directly from an owned item claim.
     *
     * This supports partial quantities, clamps to the live remaining claim amount,
     * creates or merges a listing through the normal listing-write helper, and
     * decrements the claim only after successful listing persistence.
     */
    ListingCreateResult relistClaim(Player player, UUID claimId, int quantity, long unitPrice);

    /**
     * Pays out accumulated seller earnings through Vault and clears the exact
     * durable money-claim balance only after the deposit succeeds.
     */
    ClaimMoneyResult claimEarnings(Player player);
}
