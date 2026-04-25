package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.auction.model.AdminTransactionType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class BinaryAdminStoreSupport {
    private BinaryAdminStoreSupport() {
    }

    static List<AdminTransactionRecord> loadAll(Path filePath, String magic, int version) {
        try {
            if (BinaryStoreSupport.isEmptyFile(filePath)) {
                return new ArrayList<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(filePath)) {
                BinaryStoreSupport.requireHeader(input, magic, version);

                int count = input.readInt();
                List<AdminTransactionRecord> records = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    records.add(readRecord(input));
                }

                return records;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read admin records from " + filePath, exception);
        }
    }

    static void saveAll(Path filePath, String magic, int version, List<AdminTransactionRecord> records) {
        try {
            BinaryStoreSupport.writeToTempFile(filePath, output -> {
                try {
                    BinaryStoreSupport.writeHeader(output, magic, version);
                    output.writeInt(records.size());

                    for (AdminTransactionRecord record : records) {
                        writeRecord(output, record);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed while encoding admin records.", exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write admin records to " + filePath, exception);
        }
    }

    static List<AdminTransactionRecord> pageByPredicate(
            List<AdminTransactionRecord> records,
            Predicate<AdminTransactionRecord> filter,
            int page,
            int pageSize
    ) {
        List<AdminTransactionRecord> filtered = records.stream()
                .filter(filter)
                .sorted(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis).reversed())
                .toList();
        return BinaryStoreSupport.page(filtered, page, pageSize);
    }

    static Optional<AdminTransactionRecord> findByTransactionId(List<AdminTransactionRecord> records, String transactionId) {
        return records.stream().filter(record -> transactionId.equals(record.transactionId())).findFirst();
    }

    static void purgeOldestIfOverMaxSize(Path filePath, List<AdminTransactionRecord> records, LongSupplier maxBytesSupplier, Runnable saveAction) {
        long maxBytes = maxBytesSupplier.getAsLong();
        if (maxBytes <= 0L) {
            return;
        }

        try {
            if (!Files.exists(filePath) || Files.size(filePath) <= maxBytes) {
                return;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to check admin history file size.", exception);
        }

        records.sort(Comparator.comparingLong(AdminTransactionRecord::timestampEpochMillis));

        while (!records.isEmpty()) {
            records.removeFirst();
            saveAction.run();

            try {
                if (Files.size(filePath) <= maxBytes) {
                    return;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to check admin history file size after purge.", exception);
            }
        }
    }

    private static void writeRecord(DataOutputStream output, AdminTransactionRecord record) throws IOException {
        BinaryStoreSupport.writeString(output, record.transactionId());
        output.writeLong(record.timestampEpochMillis());
        BinaryStoreSupport.writeString(output, record.transactionType().name());
        BinaryStoreSupport.writeString(output, record.listingId());
        BinaryStoreSupport.writeUuid(output, record.sellerUuid());
        BinaryStoreSupport.writeUuid(output, record.buyerUuid());
        BinaryStoreSupport.writeUuid(output, record.ownerUuid());
        BinaryStoreSupport.writeString(output, record.marketKey());
        BinaryStoreSupport.writeString(output, record.marketDisplayName());
        BinaryStoreSupport.writeString(output, record.categoryId());
        BinaryStoreSupport.writeString(output, record.itemSummary());
        output.writeInt(record.amount());
        output.writeLong(record.totalPrice());
        output.writeLong(record.unitPrice());
        BinaryStoreSupport.writeString(output, record.status());
        BinaryStoreSupport.writeString(output, record.reason());
    }

    private static AdminTransactionRecord readRecord(DataInputStream input) throws IOException {
        String transactionId = BinaryStoreSupport.readString(input);
        long timestamp = input.readLong();
        AdminTransactionType transactionType = AdminTransactionType.valueOf(BinaryStoreSupport.readString(input));
        String listingId = BinaryStoreSupport.readString(input);
        UUID sellerUuid = BinaryStoreSupport.readUuid(input);
        UUID buyerUuid = BinaryStoreSupport.readUuid(input);
        UUID ownerUuid = BinaryStoreSupport.readUuid(input);
        String marketKey = BinaryStoreSupport.readString(input);
        String marketDisplayName = BinaryStoreSupport.readString(input);
        String categoryId = BinaryStoreSupport.readString(input);
        String itemSummary = BinaryStoreSupport.readString(input);
        int amount = input.readInt();
        long totalPrice = input.readLong();
        long unitPrice = input.readLong();
        String status = BinaryStoreSupport.readString(input);
        String reason = BinaryStoreSupport.readString(input);

        return new AdminTransactionRecord(
                transactionId,
                timestamp,
                transactionType,
                listingId,
                sellerUuid,
                buyerUuid,
                ownerUuid,
                marketKey,
                marketDisplayName,
                categoryId,
                itemSummary,
                amount,
                totalPrice,
                unitPrice,
                status,
                reason
        );
    }
}
