package divinejason.divinemarketplace.auction.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Active market listing snapshot.
 *
 * Important:
 * - categoryId is stored directly on the listing so browsing/index refresh does
 *   not need to re-resolve item identity later
 * - merge should only happen when the incoming item is compatible with the
 *   existing active listing, including seller, market identity, category,
 *   price, duration, and item similarity ignoring amount
 */
public record Listing(
        UUID listingId,
        UUID sellerUuid,
        ItemStack listedItemSnapshot,
        int amount,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        long unitPrice,
        long listedAtEpochMillis,
        long listingDurationMillis
) {
}
