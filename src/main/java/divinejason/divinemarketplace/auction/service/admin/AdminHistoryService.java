package divinejason.divinemarketplace.auction.service.admin;

/*
 * Layer : service
 * Defines the public contract for the admin audit subsystem.
 *
 * All three action categories (sales, listings, claims) now route through one
 * underlying table (admin_history).  Callers still use the typed write methods
 * for clarity; reads filter by AdminTransactionType internally.
 */

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Audit subsystem entry point.
 *
 * <p>Every market action that changes money or items must produce an
 * {@link AdminTransactionRecord} so admins can investigate disputes, exploits,
 * and system errors after the fact.
 *
 * <p>All records are stored in one unified {@code admin_history} table and
 * filtered by {@code transactionType} at query time.
 */
public interface AdminHistoryService {

    // -- Writes (one per action category for call-site clarity) --

    void recordSale(AdminTransactionRecord record);
    void recordListing(AdminTransactionRecord record);
    void recordClaim(AdminTransactionRecord record);

    // -- Reads by player --

    List<AdminTransactionRecord> getSaleHistoryByPlayer(UUID playerUuid, int page, int pageSize);
    List<AdminTransactionRecord> getListingHistoryByPlayer(UUID playerUuid, int page, int pageSize);
    List<AdminTransactionRecord> getClaimHistoryByPlayer(UUID playerUuid, int page, int pageSize);

    // -- Reads by date / market key (used by export service) --

    List<AdminTransactionRecord> getSaleHistoryByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize);

    // -- Single record lookup --

    /** Looks up any record by its transaction id regardless of type. */
    Optional<AdminTransactionRecord> getSaleTransactionById(String transactionId);
}
