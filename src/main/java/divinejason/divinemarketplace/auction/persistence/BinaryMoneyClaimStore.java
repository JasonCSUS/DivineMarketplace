package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.MoneyClaimRecord;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Primary binary storage for per-player pending money balances.
 *
 * This implementation keeps the format intentionally simple:
 * - read full file
 * - mutate in memory
 * - rewrite atomically
 *
 * That is slower than append-only structures but much lower risk while the plugin
 * is still early in implementation.
 */
public final class BinaryMoneyClaimStore {
    private static final String MAGIC = "DMMONEY";
    private static final int VERSION = 1;

    private final Path filePath;
    private final Object lock = new Object();

    public BinaryMoneyClaimStore(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve(PluginDirectoryLayout.DATA_MONEY_CLAIMS));
    }

    public BinaryMoneyClaimStore(Path filePath) {
        this.filePath = filePath;
        try {
            BinaryStoreSupport.ensureFileExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize money claims file: " + filePath, exception);
        }
    }

    public long getBalanceOrZero(UUID ownerUuid) {
        synchronized (lock) {
            return loadAll().getOrDefault(ownerUuid, 0L);
        }
    }

    public void addToBalance(UUID ownerUuid, long amountToAdd) {
        if (amountToAdd <= 0L) {
            return;
        }

        synchronized (lock) {
            Map<UUID, Long> balances = loadAll();
            balances.merge(ownerUuid, amountToAdd, Long::sum);
            saveAll(balances);
        }
    }

    public void subtractFromBalance(UUID ownerUuid, long amountToSubtract) {
        if (amountToSubtract <= 0L) {
            return;
        }

        synchronized (lock) {
            Map<UUID, Long> balances = loadAll();
            long current = balances.getOrDefault(ownerUuid, 0L);
            long updated = Math.max(0L, current - amountToSubtract);

            if (updated <= 0L) {
                balances.remove(ownerUuid);
            } else {
                balances.put(ownerUuid, updated);
            }

            saveAll(balances);
        }
    }

    public void deleteIfZero(UUID ownerUuid) {
        synchronized (lock) {
            Map<UUID, Long> balances = loadAll();
            if (balances.getOrDefault(ownerUuid, 0L) <= 0L) {
                balances.remove(ownerUuid);
                saveAll(balances);
            }
        }
    }

    public boolean hasBalance(UUID ownerUuid) {
        return getBalanceOrZero(ownerUuid) > 0L;
    }

    private Map<UUID, Long> loadAll() {
        try {
            if (BinaryStoreSupport.isEmptyFile(filePath)) {
                return new LinkedHashMap<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(filePath)) {
                BinaryStoreSupport.requireHeader(input, MAGIC, VERSION);

                int count = input.readInt();
                Map<UUID, Long> balances = new LinkedHashMap<>(Math.max(16, count));

                for (int i = 0; i < count; i++) {
                    UUID ownerUuid = BinaryStoreSupport.readUuid(input);
                    long amount = input.readLong();
                    if (ownerUuid != null && amount > 0L) {
                        balances.put(ownerUuid, amount);
                    }
                }

                return balances;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read money claims from " + filePath, exception);
        }
    }

    private void saveAll(Map<UUID, Long> balances) {
        try {
            BinaryStoreSupport.writeToTempFile(filePath, output -> writeBalances(output, balances));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write money claims to " + filePath, exception);
        }
    }

    private void writeBalances(DataOutputStream output, Map<UUID, Long> balances) {
        try {
            BinaryStoreSupport.writeHeader(output, MAGIC, VERSION);
            output.writeInt(balances.size());

            for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
                BinaryStoreSupport.writeUuid(output, entry.getKey());
                output.writeLong(entry.getValue());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed while encoding money claims.", exception);
        }
    }

    public MoneyClaimRecord getRecordOrNull(UUID ownerUuid) {
        long balance = getBalanceOrZero(ownerUuid);
        return balance <= 0L ? null : new MoneyClaimRecord(ownerUuid, balance);
    }
}
