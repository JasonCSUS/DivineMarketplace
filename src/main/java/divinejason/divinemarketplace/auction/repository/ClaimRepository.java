package divinejason.divinemarketplace.auction.repository;

import divinejason.divinemarketplace.auction.model.ItemClaimRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Logical item-claim repository abstraction.
 *
 * Generic ClaimRecord is now stale; v1 separates item claims from money claims.
 */
public interface ClaimRepository {
    void saveOrReplace(ItemClaimRecord claimRecord);

    void delete(UUID claimId);

    Optional<ItemClaimRecord> findById(UUID claimId);

    List<ItemClaimRecord> findByOwner(UUID ownerUuid, int page, int pageSize);
}
