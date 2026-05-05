package divinejason.divinemarketplace.auction.service;

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

/**
 * Writes human-readable export files from SQLite admin history.
 *
 * Pure-Java design:
 * - receives already-built services and output path via constructor
 * - writes plain text files
 * - contains no Paper inventory/player/menu logic
 */
public final class DefaultAdminHistoryExportService implements AdminHistoryExportService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
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
        List<AdminTransactionRecord> records = collectPaged(page -> adminHistoryService.getSaleHistoryByDateRange(startEpochMillis, endEpochMillis, page, 500));
        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed());

        String header = "Sales history from "
                + DISPLAY_TIME.format(Instant.ofEpochMilli(startEpochMillis))
                + " to "
                + DISPLAY_TIME.format(Instant.ofEpochMilli(endEpochMillis));

        return writeExportFile("sales_range_" + FILE_TIME.format(Instant.ofEpochMilli(startEpochMillis)), header, records);
    }

    @Override
    public Path exportSalesByMarketKey(String marketKey) {
        List<AdminTransactionRecord> records = collectPaged(page -> adminHistoryService.getSaleHistoryByMarketKey(marketKey, page, 500));
        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed());
        return writeExportFile("sales_market_" + sanitizeFileToken(marketKey), "Sales history for marketKey " + marketKey, records);
    }

    @Override
    public Path exportTransactionDetail(String transactionId) {
        Optional<AdminTransactionRecord> sale = adminHistoryService.getSaleTransactionById(transactionId);
        if (sale.isPresent()) {
            return writeExportFile("transaction_" + sanitizeFileToken(transactionId), "Transaction detail", List.of(sale.get()));
        }

        Optional<AdminTransactionRecord> listing = adminHistoryService.getListingTransactionById(transactionId);
        if (listing.isPresent()) {
            return writeExportFile("transaction_" + sanitizeFileToken(transactionId), "Transaction detail", List.of(listing.get()));
        }

        Optional<AdminTransactionRecord> claim = adminHistoryService.getClaimTransactionById(transactionId);
        if (claim.isPresent()) {
            return writeExportFile("transaction_" + sanitizeFileToken(transactionId), "Transaction detail", List.of(claim.get()));
        }

        return writeExportFile("transaction_" + sanitizeFileToken(transactionId), "Transaction detail", List.of());
    }

    private List<AdminTransactionRecord> collectPaged(PageLoader pageLoader) {
        List<AdminTransactionRecord> records = new ArrayList<>();
        int page = 0;

        while (true) {
            List<AdminTransactionRecord> batch = pageLoader.load(page);
            if (batch.isEmpty()) {
                return records;
            }
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

    private String formatRecord(AdminTransactionRecord record) {
        StringBuilder builder = new StringBuilder();

        builder.append("Transaction ID: ").append(nullToEmpty(record.transactionId())).append(System.lineSeparator());
        builder.append("Timestamp: ").append(DISPLAY_TIME.format(Instant.ofEpochMilli(record.timestampEpochMillis()))).append(System.lineSeparator());
        builder.append("Type: ").append(record.transactionType()).append(System.lineSeparator());
        builder.append("Listing ID: ").append(nullToEmpty(record.listingId())).append(System.lineSeparator());
        builder.append("Seller UUID: ").append(nullToEmpty(record.sellerUuid())).append(System.lineSeparator());
        builder.append("Buyer UUID: ").append(nullToEmpty(record.buyerUuid())).append(System.lineSeparator());
        builder.append("Owner UUID: ").append(nullToEmpty(record.ownerUuid())).append(System.lineSeparator());
        builder.append("Market Key: ").append(nullToEmpty(record.marketKey())).append(System.lineSeparator());
        builder.append("Market Display Name: ").append(nullToEmpty(record.marketDisplayName())).append(System.lineSeparator());
        builder.append("Category ID: ").append(nullToEmpty(record.categoryId())).append(System.lineSeparator());
        builder.append("Item Summary: ").append(nullToEmpty(record.itemSummary())).append(System.lineSeparator());
        builder.append("Amount: ").append(record.amount()).append(System.lineSeparator());
        builder.append("Total Price: ").append(record.totalPrice()).append(System.lineSeparator());
        builder.append("Unit Price: ").append(record.unitPrice()).append(System.lineSeparator());
        builder.append("Status: ").append(nullToEmpty(record.status())).append(System.lineSeparator());
        builder.append("Reason: ").append(nullToEmpty(record.reason()));

        return builder.toString();
    }

    private String sanitizeFileToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @FunctionalInterface
    private interface PageLoader {
        List<AdminTransactionRecord> load(int page);
    }
}
