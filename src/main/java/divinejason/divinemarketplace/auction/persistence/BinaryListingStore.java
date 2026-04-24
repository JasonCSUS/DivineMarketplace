package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SortMode;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary binary persistence layer for active listing state.
 *
 * File target:
 * - data/listings.bin
 *
 * Locked storage rules:
 * - active listings only
 * - no size-based purge
 * - normal lifecycle removes records through buy/cancel/expire
 * - total price is derived from unitPrice * amount
 */
public interface BinaryListingStore {

    /**
     * Save a brand-new active listing or replace an updated active listing.
     */
    void saveOrReplace(Listing listing);

    /**
     * Remove an active listing once it is sold out, cancelled, or expired.
     */
    void delete(UUID listingId);

    /**
     * Load one active listing by id.
     */
    Optional<Listing> findById(UUID listingId);

    /**
     * Find an existing active merge target for a new listing being created.
     *
     * Merge rules should require:
     * - same seller
     * - same marketKey
     * - same marketDisplayName
     * - same unitPrice
     * - same listingDurationMillis
     * - same item similarity ignoring amount
     */
    Optional<Listing> findMergeTarget(
            UUID sellerUuid,
            String marketKey,
            String marketDisplayName,
            long unitPrice,
            long listingDurationMillis,
            ItemStack listedItemSnapshot
    );

    /**
     * Get active listings owned by one seller for My Listings UI.
     */
    List<Listing> findBySeller(UUID sellerUuid, int page, int pageSize);

    /**
     * Get active listings filtered to one market key.
     */
    List<Listing> findByMarketKey(String marketKey, SortMode sortMode, int page, int pageSize);

    /**
     * Get active listings across all market keys.
     */
    List<Listing> findAll(SortMode sortMode, int page, int pageSize);

    /**
     * Get all active listings for indexing/rebuild/recalculation passes.
     */
    Iterable<Listing> getAllActive();

    /**
     * Count active listings for one seller.
     */
    int countBySeller(UUID sellerUuid);

    /**
     * Count active listings for one market key.
     */
    int countByMarketKey(String marketKey);
}
