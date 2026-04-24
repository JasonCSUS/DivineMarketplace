package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Binary admin audit storage for listing create/cancel/expire history.
 */
public interface BinaryAdminListingsStore {
    void append(AdminTransactionRecord record);

    List<AdminTransactionRecord> findByPlayer(UUID playerUuid, int page, int pageSize);

    List<AdminTransactionRecord> findByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize);

    List<AdminTransactionRecord> findByMarketKey(String marketKey, int page, int pageSize);

    Optional<AdminTransactionRecord> findByTransactionId(String transactionId);

    void purgeOldestIfOverMaxSize();
}
