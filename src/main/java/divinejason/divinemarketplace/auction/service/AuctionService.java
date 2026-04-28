package divinejason.divinemarketplace.auction.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Optional high-level façade combining listing, purchase, claim, and expiry flows.
 *
 * This may simply delegate to the more focused services in v1:
 * - ListingService
 * - PurchaseService
 * - ClaimService
 *
 * Note:
 * - price recommendation access can remain separate and does not need to be part
 *   of this façade
 */
public interface AuctionService {

    UUID createListing(Player seller, ItemStack sourceItem, int quantity, long unitPrice);

    void buyListing(Player buyer, UUID listingId, int quantity);

    void cancelListing(Player actor, UUID listingId);

    void expireOldListings(long nowEpochMillis);
}
