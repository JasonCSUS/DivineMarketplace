package divinejason.divinemarketplace.auction.repository;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SortMode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Logical listing repository abstraction.
 *
 * This may delegate to SQLite-backed store implementations in v1.
 */
public interface ListingRepository {
    void saveOrReplace(Listing listing);

    void delete(UUID listingId);

    Optional<Listing> findById(UUID listingId);

    List<Listing> findBySeller(UUID sellerUuid, int page, int pageSize);

    List<Listing> findByMarketKey(String marketKey, SortMode sortMode, int page, int pageSize);

    List<Listing> findAll(SortMode sortMode, int page, int pageSize);

    Iterable<Listing> getAllActive();
}
