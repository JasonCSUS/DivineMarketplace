package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for listing service so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.ListingCreateResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles active listing lifecycle.
 *
 * Locked v1 behavior:
 * - normal listing source is main hand only
 * - re-read actual server-side main-hand item at confirm time
 * - remove item first, verify removal, then create or merge listing
 * - if anything fails after removal, restore the original main-hand slot
 * - merge compatible active listings for same seller/item/category/price/duration
 * - creation returns a result object rather than forcing callers to inspect
 *   exception text for ordinary user mistakes
 *
 * Note:
 * - claim-based relisting is intentionally modeled as a ClaimService flow, not
 *   the normal main-hand listing flow
 */
public interface ListingService {

    ListingCreateResult createOrMergeListing(Player seller, ItemStack sourceItem, int quantity, long unitPrice);

    void cancelListing(Player actor, UUID listingId);

    void expireDueListings(long nowEpochMillis);
}
