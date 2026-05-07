package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable listing data between marketplace services, persistence stores, commands, and GUI rendering.
 */
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Active market listing snapshot.
 *
 * Important:
 * - categoryId is stored directly on the listing so browsing/index refresh does
 *   do not need to re-resolve item identity during browse, purchase, or claim flows
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
