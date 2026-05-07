package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries item claim records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteRecordCodecSupport;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import java.sql.SQLException;
import java.util.*;
import org.bukkit.inventory.ItemStack;

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
    private final Map<UUID, Set<UUID>> claimsByOwner = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteItemClaimStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite item claims table.", exception);
        }
    }

    /** Initial load from SQLite into the memory cache. */
    public void loadFromStorage() {
        synchronized (lock) {
            try {
                cacheById.clear();
                claimsByOwner.clear();
                for (String encoded : sqliteStore.getAll(TABLE).values()) {
                    ItemClaimRecord claim = decode(encoded);
                    cacheById.put(claim.claimId(), claim);
                    indexAddClaim(claim);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load item claims from SQLite.", exception);
            }
        }
    }

    public void reload() {
        loadFromStorage();
    }

    /** Updates only the runtime cache. Callers must enqueue matching durability separately. */
    public void saveOrReplaceInMemory(ItemClaimRecord claimRecord) {
        Objects.requireNonNull(claimRecord, "claimRecord");
        synchronized (lock) {
            if (claimRecord.amount() <= 0) {
                ItemClaimRecord previous = cacheById.remove(claimRecord.claimId());
                if (previous != null) indexRemoveClaim(previous);
                return;
            }
            ItemClaimRecord previous = cacheById.put(claimRecord.claimId(), claimRecord);
            if (previous == null) {
                indexAddClaim(claimRecord);
            } else if (!previous.ownerUuid().equals(claimRecord.ownerUuid())) {
                indexRemoveClaim(previous);
                indexAddClaim(claimRecord);
            }
            // same owner replacement: claimId and ownerUuid unchanged, index stays valid
        }
    }

    /** Updates only the runtime cache. Callers must enqueue matching durability separately. */
    public void deleteInMemory(UUID claimId, UUID ownerUuid) {
        Objects.requireNonNull(claimId, "claimId");
        synchronized (lock) {
            ItemClaimRecord removed = cacheById.remove(claimId);
            if (removed != null) indexRemoveClaim(removed);
        }
    }

    public SQLiteMutation putMutation(ItemClaimRecord claimRecord) {
        Objects.requireNonNull(claimRecord, "claimRecord");
        return claimRecord.amount() <= 0
                ? SQLiteMutation.delete(TABLE, claimRecord.claimId().toString())
                : SQLiteMutation.put(TABLE, claimRecord.claimId().toString(), encode(claimRecord));
    }

    public SQLiteMutation deleteMutation(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return SQLiteMutation.delete(TABLE, claimId.toString());
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
            List<ItemClaimRecord> owned = claimsByOwner.getOrDefault(ownerUuid, Set.of()).stream()
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(ItemClaimRecord::createdAtEpochMillis).reversed())
                    .toList();
            return page(owned, page, pageSize);
        }
    }

    /** Merges into the runtime cache only. Callers must enqueue the returned record. */
    public ItemClaimRecord mergeOrCreateInMemory(ItemClaimRecord incomingClaim) {
        Objects.requireNonNull(incomingClaim, "incomingClaim");
        synchronized (lock) {
            Set<UUID> ownerClaimIds = claimsByOwner.getOrDefault(incomingClaim.ownerUuid(), Set.of());
            for (UUID existingId : ownerClaimIds) {
                ItemClaimRecord existing = cacheById.get(existingId);
                if (existing == null) continue;
                if (!itemsSimilarIgnoringAmount(existing.claimItemSnapshot(), incomingClaim.claimItemSnapshot())) continue;

                ItemClaimRecord merged = new ItemClaimRecord(
                        existing.claimId(),
                        existing.ownerUuid(),
                        incomingClaim.claimItemSnapshot().clone(),
                        existing.amount() + incomingClaim.amount(),
                        incomingClaim.createdAtEpochMillis()
                );
                cacheById.put(merged.claimId(), merged);
                // index unchanged: same claimId, same ownerUuid
                return merged;
            }

            cacheById.put(incomingClaim.claimId(), incomingClaim);
            indexAddClaim(incomingClaim);
            return incomingClaim;
        }
    }

    public int countByOwner(UUID ownerUuid) {
        synchronized (lock) {
            return claimsByOwner.getOrDefault(ownerUuid, Set.of()).size();
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
    public int maintenancePurgeOldestAbandonedClaims(long nowEpochMillis) {
        synchronized (lock) {
            long abandonMillis = ConfigService.get().itemClaimAbandonMillis();
            List<ItemClaimRecord> abandoned = cacheById.values().stream()
                    .filter(claim -> nowEpochMillis - claim.createdAtEpochMillis() >= abandonMillis)
                    .sorted(Comparator.comparingLong(ItemClaimRecord::createdAtEpochMillis))
                    .toList();

            int deleted = 0;
            for (ItemClaimRecord claim : abandoned) {
                try {
                    if (sqliteStore.delete(TABLE, claim.claimId().toString())) {
                        cacheById.remove(claim.claimId());
                        indexRemoveClaim(claim);
                        deleted++;
                    }
                } catch (SQLException exception) {
                    throw new IllegalStateException("Failed to purge abandoned item claim from SQLite.", exception);
                }
            }
            return deleted;
        }
    }

    public long estimatedPayloadBytes() {
        try {
            return sqliteStore.tablePayloadSizeBytes(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to estimate item-claim table size.", exception);
        }
    }

    private void indexAddClaim(ItemClaimRecord claim) {
        claimsByOwner.computeIfAbsent(claim.ownerUuid(), k -> new LinkedHashSet<>()).add(claim.claimId());
    }

    private void indexRemoveClaim(ItemClaimRecord claim) {
        Set<UUID> owned = claimsByOwner.get(claim.ownerUuid());
        if (owned != null) {
            owned.remove(claim.claimId());
            if (owned.isEmpty()) claimsByOwner.remove(claim.ownerUuid());
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
