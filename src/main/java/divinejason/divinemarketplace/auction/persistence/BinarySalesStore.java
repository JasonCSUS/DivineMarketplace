package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.MarketTrainingParticipation;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Player-facing exact market sale history.
 *
 * Potential hiccup intentionally left for higher-level history logic:
 * - enchant-book mixed/singular match expansion is not done in this low-level store
 * - this store only indexes exact SaleRecord.marketKey values
 */
public final class BinarySalesStore {
    private static final String MAGIC = "DMSALES";
    private static final int VERSION = 1;

    private final Path filePath;
    private final Object lock = new Object();

    public BinarySalesStore(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve(PluginDirectoryLayout.DATA_SALES));
    }

    public BinarySalesStore(Path filePath) {
        this.filePath = filePath;
        try {
            BinaryStoreSupport.ensureFileExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize sales file: " + filePath, exception);
        }
    }

    public void append(SaleRecord saleRecord) {
        synchronized (lock) {
            List<SaleRecord> sales = loadAll();
            sales.add(saleRecord);
            saveAll(sales);
        }
    }

    public List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis) {
        synchronized (lock) {
            long cutoff = System.currentTimeMillis() - lookbackMillis;
            return loadAll().stream()
                    .filter(record -> record.marketKey().equals(marketKey))
                    .filter(record -> record.soldAtEpochMillis() >= cutoff)
                    .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis))
                    .toList();
        }
    }

    public List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize) {
        synchronized (lock) {
            List<SaleRecord> filtered = loadAll().stream()
                    .filter(record -> record.marketKey().equals(marketKey))
                    .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis).reversed())
                    .toList();
            return BinaryStoreSupport.page(filtered, page, pageSize);
        }
    }

    public void purgeOldestIfOverMaxSize() {
        synchronized (lock) {
            long maxBytes = ConfigService.get().salesHistoryMaxMb() * 1024L * 1024L;
            if (maxBytes <= 0L) {
                return;
            }

            try {
                if (!Files.exists(filePath) || Files.size(filePath) <= maxBytes) {
                    return;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to check sales file size.", exception);
            }

            List<SaleRecord> sales = loadAll();
            sales.sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis));

            while (!sales.isEmpty()) {
                sales.removeFirst();
                saveAll(sales);

                try {
                    if (Files.size(filePath) <= maxBytes) {
                        return;
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to check sales file size after purge.", exception);
                }
            }
        }
    }

    private List<SaleRecord> loadAll() {
        try {
            if (BinaryStoreSupport.isEmptyFile(filePath)) {
                return new ArrayList<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(filePath)) {
                BinaryStoreSupport.requireHeader(input, MAGIC, VERSION);

                int count = input.readInt();
                List<SaleRecord> sales = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    String marketKey = BinaryStoreSupport.readString(input);
                    String marketDisplayName = BinaryStoreSupport.readString(input);
                    var snapshot = BinaryStoreSupport.readItemStack(input);
                    int amountPurchased = input.readInt();
                    long unitPrice = input.readLong();
                    long soldAt = input.readLong();
                    MarketTrainingParticipation training = MarketTrainingParticipation.valueOf(BinaryStoreSupport.readString(input));

                    if (marketKey != null && marketDisplayName != null && snapshot != null && training != null) {
                        sales.add(new SaleRecord(
                                marketKey,
                                marketDisplayName,
                                snapshot,
                                amountPurchased,
                                unitPrice,
                                soldAt,
                                training
                        ));
                    }
                }

                return sales;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sales from " + filePath, exception);
        }
    }

    private void saveAll(List<SaleRecord> sales) {
        try {
            BinaryStoreSupport.writeToTempFile(filePath, output -> writeSales(output, sales));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write sales to " + filePath, exception);
        }
    }

    private void writeSales(DataOutputStream output, List<SaleRecord> sales) {
        try {
            BinaryStoreSupport.writeHeader(output, MAGIC, VERSION);
            output.writeInt(sales.size());

            for (SaleRecord record : sales) {
                BinaryStoreSupport.writeString(output, record.marketKey());
                BinaryStoreSupport.writeString(output, record.marketDisplayName());
                BinaryStoreSupport.writeItemStack(output, record.soldItemSnapshot());
                output.writeInt(record.amountPurchased());
                output.writeLong(record.unitPrice());
                output.writeLong(record.soldAtEpochMillis());
                BinaryStoreSupport.writeString(output, record.marketTrainingParticipation().name());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed while encoding sales.", exception);
        }
    }
}
