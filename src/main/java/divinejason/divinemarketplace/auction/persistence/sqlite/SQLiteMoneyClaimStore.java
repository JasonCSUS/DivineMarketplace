package divinejason.divinemarketplace.auction.persistence.sqlite;


/*
 * File role: Persists and queries money claim records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.MoneyClaimRecord;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite-backed money claim store.
 */
public final class SQLiteMoneyClaimStore {
    private static final String TABLE = "money_claims";

    private final SQLiteStore sqliteStore;
    private final Map<UUID, Long> balances = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteMoneyClaimStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite money claims table.", exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            try {
                balances.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    balances.put(UUID.fromString(entry.getKey()), Long.parseLong(entry.getValue()));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload money claims from SQLite.", exception);
            }
        }
    }

    public long getBalanceOrZero(UUID ownerUuid) {
        synchronized (lock) {
            return balances.getOrDefault(ownerUuid, 0L);
        }
    }

    public void addToBalance(UUID ownerUuid, long amountToAdd) {
        if (amountToAdd <= 0L) {
            return;
        }

        synchronized (lock) {
            long updated = balances.getOrDefault(ownerUuid, 0L) + amountToAdd;
            balances.put(ownerUuid, updated);
            saveOne(ownerUuid, updated);
        }
    }

    public void subtractFromBalance(UUID ownerUuid, long amountToSubtract) {
        if (amountToSubtract <= 0L) {
            return;
        }

        synchronized (lock) {
            long current = balances.getOrDefault(ownerUuid, 0L);
            long updated = Math.max(0L, current - amountToSubtract);

            if (updated <= 0L) {
                balances.remove(ownerUuid);
                deleteOne(ownerUuid);
            } else {
                balances.put(ownerUuid, updated);
                saveOne(ownerUuid, updated);
            }
        }
    }

    public void deleteIfZero(UUID ownerUuid) {
        synchronized (lock) {
            if (balances.getOrDefault(ownerUuid, 0L) <= 0L) {
                balances.remove(ownerUuid);
                deleteOne(ownerUuid);
            }
        }
    }

    public boolean hasBalance(UUID ownerUuid) {
        return getBalanceOrZero(ownerUuid) > 0L;
    }

    public MoneyClaimRecord getRecordOrNull(UUID ownerUuid) {
        long balance = getBalanceOrZero(ownerUuid);
        return balance <= 0L ? null : new MoneyClaimRecord(ownerUuid, balance);
    }

    private void saveOne(UUID ownerUuid, long amount) {
        try {
            sqliteStore.put(TABLE, ownerUuid.toString(), Long.toString(amount));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write money claim balance to SQLite.", exception);
        }
    }

    private void deleteOne(UUID ownerUuid) {
        try {
            sqliteStore.delete(TABLE, ownerUuid.toString());
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete money claim balance from SQLite.", exception);
        }
    }
}
