package divinejason.divinemarketplace.auction.model;

import org.bukkit.inventory.ItemStack;

/**
 * Player-facing exact sale history record.
 *
 * This is NOT the same as admin audit history.
 * Ownership / listing-chain details stay in admin history.
 *
 * Use cases:
 * - recent player-facing market history
 * - market training input
 * - rebuilding market profiles from recent exact sales
 * - showing the exact sold item snapshot for context
 *
 * Money rule:
 * - unitPrice uses integer hundredths internally
 * - totalPrice is derived as unitPrice * amountPurchased
 *
 * Participation rule:
 * - every SaleRecord is player-facing market history by definition
 * - marketTrainingParticipation marks whether this exact sale should be used
 *   by recommendation/training logic
 */
public record SaleRecord(
        String marketKey,
        String marketDisplayName,
        ItemStack soldItemSnapshot,
        int amountPurchased,
        long unitPrice,
        long soldAtEpochMillis,
        MarketTrainingParticipation marketTrainingParticipation
) {
}
