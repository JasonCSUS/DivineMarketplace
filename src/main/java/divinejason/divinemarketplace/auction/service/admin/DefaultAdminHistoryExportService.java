package divinejason.divinemarketplace.auction.service.admin;

/*
 * Layer : service
 * Owns  : export file generation
 * Calls : DefaultAdminHistoryService (service layer)
 *
 * Generates human-readable text export files from the unified admin_history
 * table.  The old code called getListingTransactionById / getClaimTransactionById
 * on separate stores; now findTransactionById() searches the single table.
 */

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DefaultAdminHistoryExportService implements AdminHistoryExportService {

    private static final DateTimeFormatter FILE_TIME    = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DefaultAdminHistoryService adminHistoryService;
    private final Path exportDirectory;

    public DefaultAdminHistoryExportService(DefaultAdminHistoryService adminHistoryService, Path exportDirectory) {
        this.adminHistoryService = adminHistoryService;
        this.exportDirectory = exportDirectory;
    }

    @Override
    public Path exportSalesByPlayer(UUID playerUuid) {
        List<AdminTransactionRecord> records = collectPaged(page -> adminHistoryService.getSaleHistoryByPlayer(playerUuid, page, 500));
        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed());
        return writeExportFile("sales_player_" + playerUuid, "Sales history for player " + playerUuid, records);
    }

    @Override
    public Path exportSalesByDate(long dayStartEpochMillis, long dayEndEpochMillis) {
        return exportSalesByDateRange(dayStartEpochMillis, dayEndEpochMillis);
    }

    @Override
    public Path exportSalesByDateRange(long startEpochMillis, long endEpochMillis) {
        List<AdminTransactionRecord> records = collectPaged(page ->
            adminHistoryService.getSaleHistoryByDateRange(startEpochMillis, endEpochMillis, page, 500));
        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed());
        String header = "Sales history from "
            + DISPLAY_TIME.format(Instant.ofEpochMilli(startEpochMillis))
            + " to "
            + DISPLAY_TIME.format(Instant.ofEpochMilli(endEpochMillis));
        return writeExportFile("sales_range_" + FILE_TIME.format(Instant.ofEpochMilli(startEpochMillis)), header, records);
    }

    @Override
    public Path exportSalesByMarketKey(String marketKey) {
        List<AdminTransactionRecord> records = collectPaged(page ->
            adminHistoryService.getSaleHistoryByMarketKey(marketKey, page, 500));
        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed());
        return writeExportFile("sales_market_" + sanitizeFileToken(marketKey), "Sales history for marketKey " + marketKey, records);
    }

    @Override
    public Path exportTransactionDetail(String transactionId) {
        // Single unified lookup — no need to probe three separate stores.
        Optional<AdminTransactionRecord> found = adminHistoryService.findTransactionById(transactionId);
        List<AdminTransactionRecord> records = found.map(List::of).orElse(List.of());
        return writeExportFile("transaction_" + sanitizeFileToken(transactionId), "Transaction detail", records);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<AdminTransactionRecord> collectPaged(PageLoader pageLoader) {
        List<AdminTransactionRecord> records = new ArrayList<>();
        int page = 0;
        while (true) {
            List<AdminTransactionRecord> batch = pageLoader.load(page);
            if (batch.isEmpty()) return records;
            records.addAll(batch);
            page++;
        }
    }

    private Path writeExportFile(String stem, String header, List<AdminTransactionRecord> records) {
        try {
            Files.createDirectories(exportDirectory);
            Path output = exportDirectory.resolve(stem + "_" + FILE_TIME.format(Instant.now()) + ".txt");
            List<String> lines = new ArrayList<>();
            lines.add(header);
            lines.add("Generated at: " + DISPLAY_TIME.format(Instant.now()));
            lines.add("Record count: " + records.size());
            lines.add("");
            if (records.isEmpty()) {
                lines.add("No records found.");
            } else {
                for (AdminTransactionRecord record : records) {
                    lines.add(formatRecord(record));
                    lines.add("");
                }
            }
            Files.write(output, lines);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write admin history export to " + exportDirectory, exception);
        }
    }

    private String formatRecord(AdminTransactionRecord r) {
        String nl = System.lineSeparator();
        return "Transaction ID: " + nul(r.transactionId()) + nl
            + "Timestamp: "       + DISPLAY_TIME.format(Instant.ofEpochMilli(r.timestampEpochMillis())) + nl
            + "Type: "            + r.transactionType() + nl
            + "Listing ID: "      + nul(r.listingId()) + nl
            + "Seller UUID: "     + nul(r.sellerUuid()) + nl
            + "Buyer UUID: "      + nul(r.buyerUuid()) + nl
            + "Owner UUID: "      + nul(r.ownerUuid()) + nl
            + "Market Key: "      + nul(r.marketKey()) + nl
            + "Display Name: "    + nul(r.marketDisplayName()) + nl
            + "Category: "        + nul(r.categoryId()) + nl
            + "Item Summary: "    + nul(r.itemSummary()) + nl
            + "Amount: "          + r.amount() + nl
            + "Total Price: "     + r.totalPrice() + nl
            + "Unit Price: "      + r.unitPrice() + nl
            + "Status: "          + nul(r.status()) + nl
            + "Reason: "          + nul(r.reason());
    }

    private static String sanitizeFileToken(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nul(Object value) { return value == null ? "" : String.valueOf(value); }

    @FunctionalInterface
    private interface PageLoader { List<AdminTransactionRecord> load(int page); }
}
