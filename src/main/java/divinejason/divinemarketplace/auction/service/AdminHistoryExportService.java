package divinejason.divinemarketplace.auction.service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Generates human-readable export files from SQLite admin history.
 */
public interface AdminHistoryExportService {

    Path exportSalesByPlayer(UUID playerUuid);

    Path exportSalesByDate(long dayStartEpochMillis, long dayEndEpochMillis);

    Path exportSalesByDateRange(long startEpochMillis, long endEpochMillis);

    Path exportSalesByMarketKey(String marketKey);

    Path exportTransactionDetail(String transactionId);
}
