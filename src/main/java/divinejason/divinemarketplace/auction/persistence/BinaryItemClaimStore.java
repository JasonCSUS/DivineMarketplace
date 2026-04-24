package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.ItemClaimRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary binary storage for live owed item claims.
 *
 * File target:
 * - data/item_claims.bin
 *
 * Cleanup rules:
 * - never blindly FIFO purge
 * - if over soft limit, only abandoned claims may be purged
 * - purge oldest abandoned claims first
 * - if nothing is abandoned, do not purge
 */
public interface BinaryItemClaimStore {

    /**
     * Save or replace one item claim record.
     */
    void saveOrReplace(ItemClaimRecord claimRecord);

    /**
     * Remove a claim once amount reaches zero.
     */
    void delete(UUID claimId);

    /**
     * Load one item claim by id.
     */
    Optional<ItemClaimRecord> findById(UUID claimId);

    /**
     * Load paged item claims for one owner.
     */
    List<ItemClaimRecord> findByOwner(UUID ownerUuid, int page, int pageSize);

    /**
     * Merge into an existing compatible claim when safe, otherwise create a new one.
     *
     * Merge rules:
     * - same owner
     * - same claim item snapshot ignoring amount
     * - update amount
     * - refresh createdAtEpochMillis to newest merge time
     */
    ItemClaimRecord mergeOrCreate(ItemClaimRecord incomingClaim);

    /**
     * Count current claim entries for one owner.
     */
    int countByOwner(UUID ownerUuid);

    /**
     * Purge oldest abandoned claims only if the file is over its soft limit.
     *
     * PSEUDOCODE:
     * - determine whether item_claims.bin is over soft max MB
     * - gather abandoned claims using createdAtEpochMillis + configured abandon window
     * - purge oldest abandoned first until below target or no abandoned remain
     */
    void purgeOldestAbandonedIfOverSoftLimit(long nowEpochMillis);
}
