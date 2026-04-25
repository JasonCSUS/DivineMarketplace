package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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

/**
 * Binary storage for live owed item claims.
 *
 * Storage policy:
 * - claims are sharded by owner UUID hash, not by date
 * - normal owner operations mutate only one shard
 * - cleanup scans shard metadata first, then only loads candidate shards
 * - cleanup targets enough purge work to get back under (max - 10% of max)
 *
 * Important:
 * - this class is a storage/helper layer only
 * - it does not schedule cleanup on its own
 * - future services/tasks decide when to call purgeOldestAbandonedIfOverSoftLimit(...)
 */
public final class BinaryItemClaimStore {
    private static final String MAGIC = "DMICLAIM";
    private static final int VERSION = 1;
    private static final String META_MAGIC = "DMICMETA";
    private static final int META_VERSION = 1;
    private static final int SHARD_COUNT = 64;
    private static final double MIN_PURGE_RATIO = 0.10;

    private final Path shardDirectory;
    private final Object lock = new Object();

    public BinaryItemClaimStore(JavaPlugin plugin) {
        this(PluginDirectoryLayout.resolveItemClaimsDirectory(plugin.getDataFolder().toPath()));
    }

    public BinaryItemClaimStore(Path shardDirectory) {
        this.shardDirectory = shardDirectory;
        try {
            Files.createDirectories(shardDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize item claims directory: " + shardDirectory, exception);
        }
    }

    public void saveOrReplace(ItemClaimRecord claimRecord) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(claimRecord.ownerUuid());
            List<ItemClaimRecord> claims = loadShard(shardIndex);
            int existingIndex = indexOfClaim(claims, claimRecord.claimId());

            if (claimRecord.amount() <= 0) {
                if (existingIndex >= 0) {
                    claims.remove(existingIndex);
                    saveShard(shardIndex, claims);
                }
                return;
            }

            if (existingIndex >= 0) {
                claims.set(existingIndex, claimRecord);
            } else {
                claims.add(claimRecord);
            }

            saveShard(shardIndex, claims);
        }
    }

    public void delete(UUID claimId, UUID ownerUuid) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(ownerUuid);
            List<ItemClaimRecord> claims = loadShard(shardIndex);
            if (claims.removeIf(claim -> claim.claimId().equals(claimId))) {
                saveShard(shardIndex, claims);
            }
        }
    }

    /**
     * Uses an ownerUuid hint so the store can jump directly to the correct shard.
     */
    public Optional<ItemClaimRecord> findById(UUID claimId, UUID ownerUuid) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(ownerUuid);
            return loadShard(shardIndex).stream().filter(claim -> claim.claimId().equals(claimId)).findFirst();
        }
    }

    public List<ItemClaimRecord> findByOwner(UUID ownerUuid, int page, int pageSize) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(ownerUuid);
            List<ItemClaimRecord> owned = loadShard(shardIndex).stream()
                    .filter(claim -> claim.ownerUuid().equals(ownerUuid))
                    .sorted(Comparator.comparingLong(ItemClaimRecord::createdAtEpochMillis).reversed())
                    .toList();
            return BinaryStoreSupport.page(owned, page, pageSize);
        }
    }

    public ItemClaimRecord mergeOrCreate(ItemClaimRecord incomingClaim) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(incomingClaim.ownerUuid());
            List<ItemClaimRecord> claims = loadShard(shardIndex);

            for (int i = 0; i < claims.size(); i++) {
                ItemClaimRecord existing = claims.get(i);
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

                claims.set(i, merged);
                saveShard(shardIndex, claims);
                return merged;
            }

            claims.add(incomingClaim);
            saveShard(shardIndex, claims);
            return incomingClaim;
        }
    }

    public int countByOwner(UUID ownerUuid) {
        synchronized (lock) {
            int shardIndex = shardIndexForOwner(ownerUuid);
            return (int) loadShard(shardIndex).stream().filter(claim -> claim.ownerUuid().equals(ownerUuid)).count();
        }
    }

    /**
     * Cleanup helper only.
     *
     * It does not schedule itself; higher-level runtime code must decide when to call it.
     */
    public void purgeOldestAbandonedIfOverSoftLimit(long nowEpochMillis) {
        synchronized (lock) {
            long maxBytes = ConfigService.get().itemClaimsSoftMaxMb() * 1024L * 1024L;
            if (maxBytes <= 0L) {
                return;
            }

            long totalBytes = totalShardBytes();
            if (totalBytes <= maxBytes) {
                return;
            }

            long targetBytesAfterPurge = Math.max(0L, maxBytes - (long) Math.ceil(maxBytes * MIN_PURGE_RATIO));
            long abandonMillis = ConfigService.get().itemClaimAbandonMillis();
            long abandonCutoff = nowEpochMillis - abandonMillis;

            List<ShardMetadata> candidates = loadAllShardMetadata().stream()
                    .filter(meta -> meta.recordCount > 0)
                    .filter(meta -> meta.oldestCreatedAtEpochMillis > 0L)
                    .filter(meta -> meta.oldestCreatedAtEpochMillis <= abandonCutoff)
                    .sorted(Comparator.comparingLong(meta -> meta.lastActivityAtEpochMillis))
                    .toList();

            if (candidates.isEmpty()) {
                return;
            }

            for (ShardMetadata metadata : candidates) {
                List<ItemClaimRecord> claims = loadShard(metadata.shardIndex);
                int beforeSize = claims.size();

                claims.removeIf(claim -> nowEpochMillis - claim.createdAtEpochMillis() >= abandonMillis);

                if (claims.size() != beforeSize) {
                    saveShard(metadata.shardIndex, claims);
                    totalBytes = totalShardBytes();

                    if (totalBytes <= targetBytesAfterPurge) {
                        return;
                    }
                }
            }
        }
    }

    private int shardIndexForOwner(UUID ownerUuid) {
        return (ownerUuid.hashCode() & Integer.MAX_VALUE) % SHARD_COUNT;
    }

    private Path shardFile(int shardIndex) {
        return shardDirectory.resolve(String.format("shard_%02x.bin", shardIndex));
    }

    private Path metaFile(int shardIndex) {
        return shardDirectory.resolve(String.format("shard_%02x.meta", shardIndex));
    }

    private long totalShardBytes() {
        long total = 0L;
        for (int i = 0; i < SHARD_COUNT; i++) {
            Path path = shardFile(i);
            try {
                if (Files.exists(path)) {
                    total += Files.size(path);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to check claim shard size: " + path, exception);
            }
        }
        return total;
    }

    private List<ShardMetadata> loadAllShardMetadata() {
        List<ShardMetadata> metadata = new ArrayList<>(SHARD_COUNT);
        for (int i = 0; i < SHARD_COUNT; i++) {
            metadata.add(loadShardMetadata(i));
        }
        return metadata;
    }

    private ShardMetadata loadShardMetadata(int shardIndex) {
        Path path = metaFile(shardIndex);

        try {
            if (!Files.exists(path) || Files.size(path) == 0L) {
                long shardBytes = Files.exists(shardFile(shardIndex)) ? Files.size(shardFile(shardIndex)) : 0L;
                return new ShardMetadata(shardIndex, shardBytes, 0, 0L, 0L);
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(path)) {
                BinaryStoreSupport.requireHeader(input, META_MAGIC, META_VERSION);
                long fileSizeBytes = input.readLong();
                int recordCount = input.readInt();
                long oldestCreatedAt = input.readLong();
                long lastActivityAt = input.readLong();
                return new ShardMetadata(shardIndex, fileSizeBytes, recordCount, oldestCreatedAt, lastActivityAt);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read item claim shard metadata: " + path, exception);
        }
    }

    private void saveShardMetadata(int shardIndex, ShardMetadata metadata) {
        Path path = metaFile(shardIndex);

        try {
            BinaryStoreSupport.writeToTempFile(path, output -> {
                try {
                    BinaryStoreSupport.writeHeader(output, META_MAGIC, META_VERSION);
                    output.writeLong(metadata.fileSizeBytes);
                    output.writeInt(metadata.recordCount);
                    output.writeLong(metadata.oldestCreatedAtEpochMillis);
                    output.writeLong(metadata.lastActivityAtEpochMillis);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed while encoding shard metadata.", exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write item claim shard metadata: " + path, exception);
        }
    }

    private boolean itemsSimilarIgnoringAmount(ItemStack left, ItemStack right) {
        ItemStack leftCopy = left.clone();
        ItemStack rightCopy = right.clone();
        leftCopy.setAmount(1);
        rightCopy.setAmount(1);
        return leftCopy.isSimilar(rightCopy);
    }

    private int indexOfClaim(List<ItemClaimRecord> claims, UUID claimId) {
        for (int i = 0; i < claims.size(); i++) {
            if (claims.get(i).claimId().equals(claimId)) {
                return i;
            }
        }
        return -1;
    }

    private List<ItemClaimRecord> loadShard(int shardIndex) {
        Path path = shardFile(shardIndex);

        try {
            if (!Files.exists(path) || Files.size(path) == 0L) {
                return new ArrayList<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(path)) {
                BinaryStoreSupport.requireHeader(input, MAGIC, VERSION);

                int count = input.readInt();
                List<ItemClaimRecord> claims = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    UUID claimId = BinaryStoreSupport.readUuid(input);
                    UUID ownerUuid = BinaryStoreSupport.readUuid(input);
                    ItemStack itemStack = BinaryStoreSupport.readItemStack(input);
                    int amount = input.readInt();
                    long createdAt = input.readLong();

                    if (claimId != null && ownerUuid != null && itemStack != null && amount > 0) {
                        claims.add(new ItemClaimRecord(claimId, ownerUuid, itemStack, amount, createdAt));
                    }
                }

                return claims;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read item claim shard from " + path, exception);
        }
    }

    private void saveShard(int shardIndex, List<ItemClaimRecord> claims) {
        Path path = shardFile(shardIndex);

        try {
            if (claims.isEmpty()) {
                Files.deleteIfExists(path);
                saveShardMetadata(shardIndex, new ShardMetadata(shardIndex, 0L, 0, 0L, 0L));
                return;
            }

            BinaryStoreSupport.writeToTempFile(path, output -> writeClaims(output, claims));

            long fileSize = Files.size(path);
            long oldestCreatedAt = claims.stream()
                    .mapToLong(ItemClaimRecord::createdAtEpochMillis)
                    .min()
                    .orElse(0L);
            long lastActivityAt = claims.stream()
                    .mapToLong(ItemClaimRecord::createdAtEpochMillis)
                    .max()
                    .orElse(0L);

            saveShardMetadata(shardIndex, new ShardMetadata(
                    shardIndex,
                    fileSize,
                    claims.size(),
                    oldestCreatedAt,
                    lastActivityAt
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write item claim shard: " + path, exception);
        }
    }

    private void writeClaims(DataOutputStream output, List<ItemClaimRecord> claims) {
        try {
            BinaryStoreSupport.writeHeader(output, MAGIC, VERSION);
            output.writeInt(claims.size());

            for (ItemClaimRecord claim : claims) {
                BinaryStoreSupport.writeUuid(output, claim.claimId());
                BinaryStoreSupport.writeUuid(output, claim.ownerUuid());
                BinaryStoreSupport.writeItemStack(output, claim.claimItemSnapshot());
                output.writeInt(claim.amount());
                output.writeLong(claim.createdAtEpochMillis());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed while encoding item claims.", exception);
        }
    }

    private static final class ShardMetadata {
        private final int shardIndex;
        private final long fileSizeBytes;
        private final int recordCount;
        private final long oldestCreatedAtEpochMillis;
        private final long lastActivityAtEpochMillis;

        private ShardMetadata(
                int shardIndex,
                long fileSizeBytes,
                int recordCount,
                long oldestCreatedAtEpochMillis,
                long lastActivityAtEpochMillis
        ) {
            this.shardIndex = shardIndex;
            this.fileSizeBytes = fileSizeBytes;
            this.recordCount = recordCount;
            this.oldestCreatedAtEpochMillis = oldestCreatedAtEpochMillis;
            this.lastActivityAtEpochMillis = lastActivityAtEpochMillis;
        }
    }
}
