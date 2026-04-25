package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.persistence.BinaryAdminClaimsStore;
import divinejason.divinemarketplace.auction.persistence.BinaryAdminListingsStore;
import divinejason.divinemarketplace.auction.persistence.BinaryAdminSalesStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default pure-Java orchestration around the three admin history stores.
 *
 * This class intentionally stays small:
 * - write routing goes to the correct backing store
 * - read methods delegate to the matching store
 * - no Paper player/menu logic lives here
 */
public final class DefaultAdminHistoryService implements AdminHistoryService {
    private final BinaryAdminSalesStore adminSalesStore;
    private final BinaryAdminListingsStore adminListingsStore;
    private final BinaryAdminClaimsStore adminClaimsStore;

    public DefaultAdminHistoryService(
            BinaryAdminSalesStore adminSalesStore,
            BinaryAdminListingsStore adminListingsStore,
            BinaryAdminClaimsStore adminClaimsStore
    ) {
        this.adminSalesStore = adminSalesStore;
        this.adminListingsStore = adminListingsStore;
        this.adminClaimsStore = adminClaimsStore;
    }

    @Override
    public void recordSale(AdminTransactionRecord record) {
        adminSalesStore.append(record);
    }

    @Override
    public void recordListing(AdminTransactionRecord record) {
        adminListingsStore.append(record);
    }

    @Override
    public void recordClaim(AdminTransactionRecord record) {
        adminClaimsStore.append(record);
    }

    @Override
    public List<AdminTransactionRecord> getSaleHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return adminSalesStore.findByPlayer(playerUuid, page, pageSize);
    }

    @Override
    public List<AdminTransactionRecord> getListingHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return adminListingsStore.findByPlayer(playerUuid, page, pageSize);
    }

    @Override
    public List<AdminTransactionRecord> getClaimHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return adminClaimsStore.findByPlayer(playerUuid, page, pageSize);
    }

    @Override
    public List<AdminTransactionRecord> getSaleHistoryByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize) {
        return adminSalesStore.findByDateRange(startEpochMillis, endEpochMillis, page, pageSize);
    }

    @Override
    public Optional<AdminTransactionRecord> getSaleTransactionById(String transactionId) {
        return adminSalesStore.findByTransactionId(transactionId);
    }

    public List<AdminTransactionRecord> getSaleHistoryByMarketKey(String marketKey, int page, int pageSize) {
        return adminSalesStore.findByMarketKey(marketKey, page, pageSize);
    }

    public Optional<AdminTransactionRecord> getListingTransactionById(String transactionId) {
        return adminListingsStore.findByTransactionId(transactionId);
    }

    public Optional<AdminTransactionRecord> getClaimTransactionById(String transactionId) {
        return adminClaimsStore.findByTransactionId(transactionId);
    }
}
