package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * Active listing record stored in listings.bin.
 *
 * V1 rules locked here:
 * - listings.bin stores active listings only
 * - listing source is one held item / one explicit source stack only
 * - item must be removed server-side and reserved before a listing is created or merged
 * - amount is a logical market quantity and may represent grouped unstackables
 * - unitPrice is the authoritative stored price
 * - total price is derived: unitPrice * amount
 * - expiration is derived: listedAtEpochMillis + listingDurationMillis
 * - when mergeable stock is added, amount increases and listedAtEpochMillis is bumped
 *   to the newest listing time for player convenience
 *
 * Listing merge should only happen when the incoming item is compatible with the
 * existing active listing, including seller, market identity, price, duration, and
 * item similarity ignoring amount.
 */
public record Listing(
        UUID listingId,
        UUID sellerUuid,
        ItemStack listedItemSnapshot,
        int amount,
        String marketKey,
        String marketDisplayName,
        long unitPrice,
        long listedAtEpochMillis,
        long listingDurationMillis
) {
}
