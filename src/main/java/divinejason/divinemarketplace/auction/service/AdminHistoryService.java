package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Audit subsystem entry point.
 *
 * Mandatory behavior:
 * - all market actions must be written to binary admin history
 * - sales/listings/claims are split into separate files for easier querying
 */
public interface AdminHistoryService {

    void recordSale(AdminTransactionRecord record);

    void recordListing(AdminTransactionRecord record);

    void recordClaim(AdminTransactionRecord record);

    List<AdminTransactionRecord> getSaleHistoryByPlayer(UUID playerUuid, int page, int pageSize);

    List<AdminTransactionRecord> getListingHistoryByPlayer(UUID playerUuid, int page, int pageSize);

    List<AdminTransactionRecord> getClaimHistoryByPlayer(UUID playerUuid, int page, int pageSize);

    List<AdminTransactionRecord> getSaleHistoryByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize);

    Optional<AdminTransactionRecord> getSaleTransactionById(String transactionId);
}
