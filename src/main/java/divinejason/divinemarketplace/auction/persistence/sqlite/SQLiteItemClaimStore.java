package divinejason.divinemarketplace.auction.persistence.sqlite;

import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecordCodecSupport;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.*;

/**
 * SQLite-backed item claim store.
 *
 * Claim persistence is intentionally one SQLite table. Per-player isolation for
 * simultaneous marketplace users is handled by GUI/prompt session maps, not by
 * splitting claim storage into owner-specific files.
 */
public final class SQLiteItemClaimStore {
    private static final String TABLE = "item_claims";

    private final SQLiteStore sqliteStore;
    private final Map<UUID, ItemClaimRecord> cacheById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteItemClaimStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite item claims table.", exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            try {
                cacheById.clear();
                for (String encoded : sqliteStore.getAll(TABLE).values()) {
                    ItemClaimRecord claim = decode(encoded);
                    cacheById.put(claim.claimId(), claim);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload item claims from SQLite.", exception);
            }
        }
    }

    public void saveOrReplace(ItemClaimRecord claimRecord) {
        synchronized (lock) {
            if (claimRecord.amount() <= 0) {
                delete(claimRecord.claimId(), claimRecord.ownerUuid());
                return;
            }

            cacheById.put(claimRecord.claimId(), claimRecord);
            try {
                sqliteStore.put(TABLE, claimRecord.claimId().toString(), encode(claimRecord));
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to write item claim to SQLite.", exception);
            }
        }
    }

    public void delete(UUID claimId, UUID ownerUuid) {
        synchronized (lock) {
            cacheById.remove(claimId);
            try {
                sqliteStore.delete(TABLE, claimId.toString());
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to delete item claim from SQLite.", exception);
            }
        }
    }

    public Optional<ItemClaimRecord> findById(UUID claimId, UUID ownerUuid) {
        synchronized (lock) {
            ItemClaimRecord record = cacheById.get(claimId);
            if (record == null || !record.ownerUuid().equals(ownerUuid)) {
                return Optional.empty();
            }
            return Optional.of(record);
        }
    }

    public List<ItemClaimRecord> findByOwner(UUID ownerUuid, int page, int pageSize) {
        synchronized (lock) {
            List<ItemClaimRecord> owned = cacheById.values().stream()
                    .filter(claim -> claim.ownerUuid().equals(ownerUuid))
                    .sorted(Comparator.comparingLong(ItemClaimRecord::createdAtEpochMillis).reversed())
                    .toList();
            return page(owned, page, pageSize);
        }
    }

    public ItemClaimRecord mergeOrCreate(ItemClaimRecord incomingClaim) {
        synchronized (lock) {
            for (ItemClaimRecord existing : cacheById.values()) {
                if (!existing.ownerUuid().equals(incomingClaim.ownerUuid())) {
                    continue;
                }
                if (!itemsSimilarIgnoringAmount(existing.claimItemSnapshot(), incomingClaim.claimItemSnapshot())) {
                    continue;
                }

                ItemClaimRecord merged = new ItemClaimRecord(
                        existing.claimId(),
                        existing.ownerUuid(),
                        incomingClaim.claimItemSnapshot().clone(),
                        existing.amount() + incomingClaim.amount(),
                        incomingClaim.createdAtEpochMillis()
                );
                saveOrReplace(merged);
                return merged;
            }

            saveOrReplace(incomingClaim);
            return incomingClaim;
        }
    }

    public int countByOwner(UUID ownerUuid) {
        synchronized (lock) {
            return (int) cacheById.values().stream().filter(claim -> claim.ownerUuid().equals(ownerUuid)).count();
        }
    }

    public int countAll() {
        synchronized (lock) {
            return cacheById.size();
        }
    }

    /**
     * Purges abandoned item claims only after an external storage-size/pressure gate decides cleanup is needed.
     * Returns the number of removed claims so callers can warn admins when storage pressure remains actionable.
     */
    public int purgeOldestAbandonedClaims(long nowEpochMillis) {
        synchronized (lock) {
            long abandonMillis = ConfigService.get().itemClaimAbandonMillis();
            List<ItemClaimRecord> abandoned = cacheById.values().stream()
                    .filter(claim -> nowEpochMillis - claim.createdAtEpochMillis() >= abandonMillis)
                    .sorted(Comparator.comparingLong(ItemClaimRecord::createdAtEpochMillis))
                    .toList();

            int deleted = 0;
            for (ItemClaimRecord claim : abandoned) {
                delete(claim.claimId(), claim.ownerUuid());
                deleted++;
            }
            return deleted;
        }
    }

    private boolean itemsSimilarIgnoringAmount(ItemStack left, ItemStack right) {
        ItemStack leftCopy = left.clone();
        ItemStack rightCopy = right.clone();
        leftCopy.setAmount(1);
        rightCopy.setAmount(1);
        return leftCopy.isSimilar(rightCopy);
    }

    private List<ItemClaimRecord> page(List<ItemClaimRecord> input, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= input.size()) {
            return List.of();
        }
        int end = Math.min(input.size(), start + pageSize);
        return List.copyOf(input.subList(start, end));
    }

    private String encode(ItemClaimRecord claimRecord) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeUuid(output, claimRecord.claimId());
            SQLiteRecordCodecSupport.writeUuid(output, claimRecord.ownerUuid());
            SQLiteRecordCodecSupport.writeItemStack(output, claimRecord.claimItemSnapshot());
            output.writeInt(claimRecord.amount());
            output.writeLong(claimRecord.createdAtEpochMillis());
        });
    }

    private ItemClaimRecord decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new ItemClaimRecord(
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readItemStack(input),
                input.readInt(),
                input.readLong()
        ));
    }
}
