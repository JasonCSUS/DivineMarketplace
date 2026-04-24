package divinejason.divinemarketplace.auction.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles active listing lifecycle.
 *
 * Locked v1 behavior:
 * - listing source is one held item / one explicit source stack only
 * - re-read actual server-side item at confirm time
 * - make separate clones for removal and reservation/listing payload
 * - remove item first, verify removal, then reserve, then create/merge listing
 * - if anything fails after removal, return reserved item or recover safely
 * - merge compatible active listings for same seller/item/price/duration
 */
public interface ListingService {

    /**
     * Create a new listing or merge into an existing compatible active listing.
     *
     * PSEUDOCODE:
     * - resolve player listing policy
     * - re-read held/source item from server inventory
     * - validate requested quantity and unit price
     * - validate seller active listing limit
     * - resolve item identity / category / market key
     * - safely remove requested quantity from inventory
     * - reserve removed item snapshot
     * - attempt merge target lookup
     * - if merge target exists:
     *   - increase amount
     *   - bump listedAtEpochMillis to newest listing time
     *   - save updated listing
     * - else create new Listing and save
     * - write admin listing history
     * - refresh category/listing indexes
     */
    UUID createOrMergeListing(Player seller, ItemStack sourceItem, int quantity, long unitPrice);

    /**
     * Cancel an active listing and move remaining quantity to item claims.
     */
    void cancelListing(Player actor, UUID listingId);

    /**
     * Expire due active listings and move remaining quantity to item claims.
     */
    void expireDueListings(long nowEpochMillis);
}
