package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * Durable item claim record stored in item_claims.bin.
 *
 * Locked v1 rules:
 * - item claims are separate from money claims
 * - amount is remaining claim quantity
 * - merge similar claims for the same owner when safe
 * - merging refreshes createdAtEpochMillis to the newest merge time
 * - normal click should redeem one safe stack-sized chunk
 * - shift-click may redeem as much as safely fits in inventory
 * - remove the claim when amount reaches 0
 * - abandonment is derived from createdAtEpochMillis
 */
public record ItemClaimRecord(
        UUID claimId,
        UUID ownerUuid,
        ItemStack claimItemSnapshot,
        int amount,
        long createdAtEpochMillis
) {
}
