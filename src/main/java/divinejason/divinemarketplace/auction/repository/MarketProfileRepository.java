package divinejason.divinemarketplace.auction.repository;

import divinejason.divinemarketplace.auction.model.MarketProfile;

/**
 * Binary persistence contract for market profile cache state.
 */
public interface MarketProfileRepository {
    MarketProfile getOrDefault(String marketKey);

    void save(MarketProfile marketProfile);

    Iterable<MarketProfile> getAllProfiles();
}
