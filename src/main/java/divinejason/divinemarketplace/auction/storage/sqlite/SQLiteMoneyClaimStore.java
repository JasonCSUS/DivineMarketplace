package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries money claim records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.MoneyClaimRecord;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
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
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite money claims table.", exception);
        }
    }

    /** Initial load from SQLite into the memory cache. */
    public void loadFromStorage() {
        synchronized (lock) {
            try {
                balances.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    balances.put(UUID.fromString(entry.getKey()), Long.parseLong(entry.getValue()));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load money claims from SQLite.", exception);
            }
        }
    }

    public void reload() {
        loadFromStorage();
    }

    public long getBalanceOrZero(UUID ownerUuid) {
        synchronized (lock) {
            return balances.getOrDefault(ownerUuid, 0L);
        }
    }

    public long addToBalanceInMemory(UUID ownerUuid, long amountToAdd) {
        if (amountToAdd <= 0L) {
            return getBalanceOrZero(ownerUuid);
        }

        synchronized (lock) {
            long updated = balances.getOrDefault(ownerUuid, 0L) + amountToAdd;
            balances.put(ownerUuid, updated);
            return updated;
        }
    }

    public long subtractFromBalanceInMemory(UUID ownerUuid, long amountToSubtract) {
        if (amountToSubtract <= 0L) {
            return getBalanceOrZero(ownerUuid);
        }

        synchronized (lock) {
            long current = balances.getOrDefault(ownerUuid, 0L);
            long updated = Math.max(0L, current - amountToSubtract);

            if (updated <= 0L) {
                balances.remove(ownerUuid);
            } else {
                balances.put(ownerUuid, updated);
            }
            return updated;
        }
    }

    public void setBalanceInMemory(UUID ownerUuid, long amount) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        synchronized (lock) {
            if (amount <= 0L) {
                balances.remove(ownerUuid);
            } else {
                balances.put(ownerUuid, amount);
            }
        }
    }

    public SQLiteMutation putOrDeleteMutation(UUID ownerUuid, long amount) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        return amount <= 0L
                ? SQLiteMutation.delete(TABLE, ownerUuid.toString())
                : SQLiteMutation.put(TABLE, ownerUuid.toString(), Long.toString(amount));
    }

    public boolean hasBalance(UUID ownerUuid) {
        return getBalanceOrZero(ownerUuid) > 0L;
    }

    public MoneyClaimRecord getRecordOrNull(UUID ownerUuid) {
        long balance = getBalanceOrZero(ownerUuid);
        return balance <= 0L ? null : new MoneyClaimRecord(ownerUuid, balance);
    }


}
