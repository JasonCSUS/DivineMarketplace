package divinejason.divinemarketplace.auction.service.admin;

/*
 * Layer : service
 * Owns  : admin audit read orchestration
 * Calls : MarketEventService (service layer)
 *
 * Write methods (recordSale/recordListing/recordClaim) are now no-ops.
 * All market actions write directly to MarketEventService via their own service classes.
 * Read methods project MarketEventRecord -> AdminTransactionRecord for backward
 * compatibility with commands and GUI rendering.
 */

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.service.event.MarketEventService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DefaultAdminHistoryService implements AdminHistoryService {

    private final MarketEventService marketEventService;

    public DefaultAdminHistoryService(MarketEventService marketEventService) {
        this.marketEventService = Objects.requireNonNull(marketEventService, "marketEventService");
    }

    // -------------------------------------------------------------------------
    // Write methods — no-ops; market actions write via their own services now
    // -------------------------------------------------------------------------

    @Override @Deprecated public void recordSale(AdminTransactionRecord record)    {}
    @Override @Deprecated public void recordListing(AdminTransactionRecord record) {}
    @Override @Deprecated public void recordClaim(AdminTransactionRecord record)   {}

    // -------------------------------------------------------------------------
    // Read methods — project MarketEventRecord -> AdminTransactionRecord
    // -------------------------------------------------------------------------

    @Override
    public List<AdminTransactionRecord> getSaleHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByPlayerAndType(playerUuid, MarketEventType.BUY, page, pageSize));
    }

    @Override
    public List<AdminTransactionRecord> getListingHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByPlayerAndType(playerUuid, MarketEventType.LIST, page, pageSize));
    }

    @Override
    public List<AdminTransactionRecord> getClaimHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByPlayerAndTypes(
                playerUuid,
                Set.of(MarketEventType.CLAIM_ITEM, MarketEventType.CLAIM_MONEY),
                page, pageSize));
    }

    @Override
    public List<AdminTransactionRecord> getSaleHistoryByDateRange(
            long startEpochMillis, long endEpochMillis, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByDateRange(startEpochMillis, endEpochMillis, page, pageSize));
    }

    @Override
    public Optional<AdminTransactionRecord> getSaleTransactionById(String transactionId) {
        return marketEventService.findById(transactionId).map(this::toAdminRecord);
    }

    public Optional<AdminTransactionRecord> findTransactionById(String transactionId) {
        return getSaleTransactionById(transactionId);
    }

    public List<AdminTransactionRecord> getSaleHistoryByMarketKey(String marketKey, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByMarketKey(marketKey, page, pageSize));
    }

    public List<AdminTransactionRecord> getAllHistoryByPlayer(UUID playerUuid, int page, int pageSize) {
        return toAdminRecords(marketEventService.findByPlayer(playerUuid, page, pageSize));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<AdminTransactionRecord> toAdminRecords(List<MarketEventRecord> events) {
        return events.stream().map(this::toAdminRecord).toList();
    }

    private AdminTransactionRecord toAdminRecord(MarketEventRecord event) {
        return new AdminTransactionRecord(
                event.eventId(),
                event.timestampEpochMillis(),
                AdminTransactionType.valueOf(event.eventType().name()),
                event.listingId(),
                event.sellerUuid(),
                event.buyerUuid(),
                event.ownerUuid(),
                event.marketKey(),
                event.marketDisplayName(),
                event.categoryId(),
                event.itemSummary(),
                event.amount(),
                event.totalPrice(),
                event.unitPrice(),
                event.status(),
                event.reason()
        );
    }
}
