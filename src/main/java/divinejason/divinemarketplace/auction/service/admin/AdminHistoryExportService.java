package divinejason.divinemarketplace.auction.service.admin;

/*
 * Layer : service
 * Defines the export file generation contract.
 *
 * Exports are generated from the unified admin_history table.
 * exportTransactionDetail() now calls findTransactionById() on one table
 * instead of probing three separate stores.
 */

import java.nio.file.Path;
import java.util.UUID;

/** Generates human-readable text export files from admin audit history. */
public interface AdminHistoryExportService {

    Path exportSalesByPlayer(UUID playerUuid);

    Path exportSalesByDate(long dayStartEpochMillis, long dayEndEpochMillis);

    Path exportSalesByDateRange(long startEpochMillis, long endEpochMillis);

    Path exportSalesByMarketKey(String marketKey);

    /** Exports detail for any transaction id, regardless of action type. */
    Path exportTransactionDetail(String transactionId);
}
