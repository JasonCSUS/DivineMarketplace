package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.AdminTransactionRecord;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BinaryAdminSalesStore {
    private static final String MAGIC = "DMASALE";
    private static final int VERSION = 1;

    private final Path filePath;
    private final Object lock = new Object();

    public BinaryAdminSalesStore(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve(PluginDirectoryLayout.DATA_ADMIN_SALES));
    }

    public BinaryAdminSalesStore(Path filePath) {
        this.filePath = filePath;
        try {
            BinaryStoreSupport.ensureFileExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize admin history file: " + filePath, exception);
        }
    }

    public void append(AdminTransactionRecord record) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            records.add(record);
            BinaryAdminStoreSupport.saveAll(filePath, MAGIC, VERSION, records);
        }
    }

    public List<AdminTransactionRecord> findByPlayer(UUID playerUuid, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            return BinaryAdminStoreSupport.pageByPredicate(
                    records,
                    record -> playerUuid.equals(record.sellerUuid()) || playerUuid.equals(record.buyerUuid()) || playerUuid.equals(record.ownerUuid()),
                    page,
                    pageSize
            );
        }
    }

    public List<AdminTransactionRecord> findByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            return BinaryAdminStoreSupport.pageByPredicate(
                    records,
                    record -> record.timestampEpochMillis() >= startEpochMillis && record.timestampEpochMillis() <= endEpochMillis,
                    page,
                    pageSize
            );
        }
    }

    public List<AdminTransactionRecord> findByMarketKey(String marketKey, int page, int pageSize) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            return BinaryAdminStoreSupport.pageByPredicate(records, record -> marketKey.equals(record.marketKey()), page, pageSize);
        }
    }

    public Optional<AdminTransactionRecord> findByTransactionId(String transactionId) {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            return BinaryAdminStoreSupport.findByTransactionId(records, transactionId);
        }
    }

    public void purgeOldestIfOverMaxSize() {
        synchronized (lock) {
            List<AdminTransactionRecord> records = BinaryAdminStoreSupport.loadAll(filePath, MAGIC, VERSION);
            BinaryAdminStoreSupport.purgeOldestIfOverMaxSize(
                    filePath,
                    records,
                    () -> ConfigService.get().adminSalesHistoryMaxMb() * 1024L * 1024L,
                    () -> BinaryAdminStoreSupport.saveAll(filePath, MAGIC, VERSION, records)
            );
        }
    }
}
