package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries listing records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBatch;
import divinejason.divinemarketplace.storage.sqlite.SQLiteWriteBehindQueue;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;

/**
 * SQLite-backed listing store.
 *
 * Runtime reads are cache-backed and listing mutations update memory immediately. SQLite writes are coalesced and
 * flushed through the shared database writer so high-volume listing activity does not stall the server thread.
 */
public final class SQLiteListingStore {
    private static final String TABLE = "listings";
    private static final int AUTO_FLUSH_OPERATION_THRESHOLD = 50;

    private final SQLiteStore sqliteStore;
    private final SQLiteWriteBehindQueue writeBehindQueue;
    private final Logger logger;

    private final Map<UUID, Listing> cacheById = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> listingsBySeller = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> listingsByMarketKey = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> listingsByCategory = new LinkedHashMap<>();

    private final Map<UUID, String> pendingUpsertsById = new LinkedHashMap<>();
    private final Set<UUID> pendingDeletesById = new LinkedHashSet<>();
    private final Object lock = new Object();
    private final AtomicBoolean asyncFlushInProgress = new AtomicBoolean(false);
    private volatile CompletableFuture<Void> activeFlush = CompletableFuture.completedFuture(null);

    public SQLiteListingStore(SQLiteStore sqliteStore) {
        this(sqliteStore, null, Logger.getLogger(SQLiteListingStore.class.getName()));
    }

    public SQLiteListingStore(SQLiteStore sqliteStore, Logger logger) {
        this(sqliteStore, null, logger);
    }

    public SQLiteListingStore(SQLiteStore sqliteStore, SQLiteWriteBehindQueue writeBehindQueue, Logger logger) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        this.writeBehindQueue = writeBehindQueue;
        this.logger = Objects.requireNonNull(logger, "logger");
        try {
            sqliteStore.ensureTable(TABLE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite listings table.", exception);
        }
    }

    /** Initial load from SQLite into the memory cache. Does not flush pending writes. */
    public void loadFromStorage() {
        synchronized (lock) {
            try {
                cacheById.clear();
                listingsBySeller.clear();
                listingsByMarketKey.clear();
                listingsByCategory.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    Listing listing = decode(entry.getValue());
                    cacheById.put(listing.listingId(), listing);
                    indexAdd(listing);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load listings from SQLite.", exception);
            }
        }
    }

    /** Flushes pending writes then reloads from SQLite. Use for /market reload. */
    public void reload() {
        flushPendingWritesBlocking();
        loadFromStorage();
    }

    /** Updates only the runtime cache. Callers must enqueue matching durability separately. */
    public void saveOrReplaceInMemory(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        synchronized (lock) {
            Listing previous = cacheById.put(listing.listingId(), listing);
            if (previous != null) indexRemove(previous);
            indexAdd(listing);
            pendingDeletesById.remove(listing.listingId());
            pendingUpsertsById.remove(listing.listingId());
        }
    }

    /** Updates only the runtime cache. Callers must enqueue matching durability separately. */
    public void deleteInMemory(UUID listingId) {
        Objects.requireNonNull(listingId, "listingId");
        synchronized (lock) {
            Listing removed = cacheById.remove(listingId);
            if (removed != null) indexRemove(removed);
            pendingUpsertsById.remove(listingId);
            pendingDeletesById.remove(listingId);
        }
    }

    public SQLiteMutation putMutation(Listing listing) {
        Objects.requireNonNull(listing, "listing");
        return SQLiteMutation.put(TABLE, listing.listingId().toString(), encode(listing));
    }

    public SQLiteMutation deleteMutation(UUID listingId) {
        Objects.requireNonNull(listingId, "listingId");
        return SQLiteMutation.delete(TABLE, listingId.toString());
    }

    public int pendingWriteCount() {
        synchronized (lock) {
            return pendingWriteCountLocked();
        }
    }

    public void flushPendingWritesAsync() {
        if (writeBehindQueue != null) {
            writeBehindQueue.flushAsync();
            return;
        }

        ListingWriteBatch batch;
        synchronized (lock) {
            if (asyncFlushInProgress.get()) {
                return;
            }
            batch = drainPendingWritesLocked();
            if (batch.isEmpty()) {
                return;
            }
            asyncFlushInProgress.set(true);
        }

        CompletableFuture<Void> flush = writeBatchAsync(batch);
        CompletableFuture<Void> trackedFlush = flush.whenComplete((ignored, throwable) -> {
            boolean shouldFlushAgain;
            if (throwable != null) {
                requeueBatchIfNotOverridden(batch);
                logger.log(Level.SEVERE, "Failed to flush queued listing writes to SQLite. They were re-queued for the next flush.", unwrapCompletionException(throwable));
                shouldFlushAgain = false;
            } else {
                synchronized (lock) {
                    shouldFlushAgain = pendingWriteCountLocked() >= AUTO_FLUSH_OPERATION_THRESHOLD;
                }
            }

            asyncFlushInProgress.set(false);
            activeFlush = CompletableFuture.completedFuture(null);

            if (shouldFlushAgain) {
                flushPendingWritesAsync();
            }
        });
        activeFlush = trackedFlush;
    }

    public void flushPendingWritesBlocking() {
        if (writeBehindQueue != null) {
            writeBehindQueue.flushBlocking();
            return;
        }

        while (true) {
            waitForActiveFlushToFinish();

            ListingWriteBatch batch;
            synchronized (lock) {
                if (asyncFlushInProgress.get()) {
                    continue;
                }
                batch = drainPendingWritesLocked();
            }
            if (batch.isEmpty()) {
                return;
            }

            try {
                writeBatch(batch);
            } catch (SQLException exception) {
                requeueBatchIfNotOverridden(batch);
                throw new IllegalStateException("Failed to flush queued listing writes to SQLite.", exception);
            }
        }
    }

    public Optional<Listing> findById(UUID listingId) {
        synchronized (lock) {
            return Optional.ofNullable(cacheById.get(listingId));
        }
    }

    public Optional<Listing> findMergeTarget(
            UUID sellerUuid,
            String marketKey,
            String marketDisplayName,
            String categoryId,
            long unitPrice,
            long listingDurationMillis,
            ItemStack listedItemSnapshot
    ) {
        synchronized (lock) {
            Set<UUID> sellerIds = listingsBySeller.getOrDefault(sellerUuid, Set.of());
            Set<UUID> keyIds = listingsByMarketKey.getOrDefault(marketKey, Set.of());
            return sellerIds.stream()
                    .filter(keyIds::contains)
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .filter(listing -> listing.marketDisplayName().equals(marketDisplayName))
                    .filter(listing -> listing.categoryId().equals(categoryId))
                    .filter(listing -> listing.unitPrice() == unitPrice)
                    .filter(listing -> listing.listingDurationMillis() == listingDurationMillis)
                    .filter(listing -> itemsSimilarIgnoringAmount(listing.listedItemSnapshot(), listedItemSnapshot))
                    .findFirst();
        }
    }

    public List<Listing> findBySeller(UUID sellerUuid, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = listingsBySeller.getOrDefault(sellerUuid, Set.of()).stream()
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(Listing::listedAtEpochMillis).reversed())
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<Listing> findByMarketKey(String marketKey, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = listingsByMarketKey.getOrDefault(marketKey, Set.of()).stream()
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .sorted(listingComparator(sortMode))
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<Listing> findActiveByMarketKeyUnsorted(String marketKey) {
        synchronized (lock) {
            return listingsByMarketKey.getOrDefault(marketKey, Set.of()).stream()
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    public List<Listing> findByCategoryId(String categoryId, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = listingsByCategory.getOrDefault(categoryId, Set.of()).stream()
                    .map(cacheById::get)
                    .filter(Objects::nonNull)
                    .sorted(listingComparator(sortMode))
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<Listing> findAll(SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> all = cacheById.values().stream()
                    .sorted(listingComparator(sortMode))
                    .toList();
            return page(all, page, pageSize);
        }
    }

    public Iterable<Listing> getAllActive() {
        return findAllActiveUnsorted();
    }

    public List<Listing> findAllActiveUnsorted() {
        synchronized (lock) {
            return List.copyOf(cacheById.values());
        }
    }

    public int countAll() {
        synchronized (lock) {
            return cacheById.size();
        }
    }

    public Set<String> findActiveCategoryIds() {
        synchronized (lock) {
            return Set.copyOf(listingsByCategory.keySet());
        }
    }

    public int countBySeller(UUID sellerUuid) {
        synchronized (lock) {
            return listingsBySeller.getOrDefault(sellerUuid, Set.of()).size();
        }
    }

    public int countByMarketKey(String marketKey) {
        synchronized (lock) {
            return listingsByMarketKey.getOrDefault(marketKey, Set.of()).size();
        }
    }

    public int countByCategoryId(String categoryId) {
        synchronized (lock) {
            return listingsByCategory.getOrDefault(categoryId, Set.of()).size();
        }
    }

    private void waitForActiveFlushToFinish() {
        CompletableFuture<Void> flush = activeFlush;
        try {
            flush.join();
        } catch (CompletionException exception) {
            logger.log(Level.WARNING, "Previous async listing flush failed before blocking flush.", unwrapCompletionException(exception));
        }
    }

    private CompletableFuture<Void> writeBatchAsync(ListingWriteBatch batch) {
        CompletableFuture<Integer> deleteFuture = batch.deleteIds().isEmpty()
                ? CompletableFuture.completedFuture(0)
                : sqliteStore.deleteBatchAsync(TABLE, stringifyIds(batch.deleteIds()));

        return deleteFuture
                .thenCompose(ignored -> batch.upsertsById().isEmpty()
                        ? CompletableFuture.completedFuture(0)
                        : sqliteStore.putBatchAsync(TABLE, stringifyUpserts(batch.upsertsById())))
                .thenAccept(ignored -> { });
    }

    private void writeBatch(ListingWriteBatch batch) throws SQLException {
        if (!batch.deleteIds().isEmpty()) {
            sqliteStore.deleteBatch(TABLE, stringifyIds(batch.deleteIds()));
        }
        if (!batch.upsertsById().isEmpty()) {
            sqliteStore.putBatch(TABLE, stringifyUpserts(batch.upsertsById()));
        }
    }

    private ListingWriteBatch drainPendingWritesLocked() {
        if (pendingUpsertsById.isEmpty() && pendingDeletesById.isEmpty()) {
            return ListingWriteBatch.empty();
        }

        Map<UUID, String> upserts = new LinkedHashMap<>(pendingUpsertsById);
        Set<UUID> deletes = new LinkedHashSet<>(pendingDeletesById);
        pendingUpsertsById.clear();
        pendingDeletesById.clear();
        return new ListingWriteBatch(upserts, deletes);
    }

    private void requeueBatchIfNotOverridden(ListingWriteBatch batch) {
        synchronized (lock) {
            for (UUID deleteId : batch.deleteIds()) {
                if (!pendingUpsertsById.containsKey(deleteId)) {
                    pendingDeletesById.add(deleteId);
                }
            }

            for (Map.Entry<UUID, String> entry : batch.upsertsById().entrySet()) {
                UUID listingId = entry.getKey();
                if (!pendingDeletesById.contains(listingId) && !pendingUpsertsById.containsKey(listingId)) {
                    pendingUpsertsById.put(listingId, entry.getValue());
                }
            }
        }
    }

    private int pendingWriteCountLocked() {
        return pendingUpsertsById.size() + pendingDeletesById.size();
    }

    private void indexAdd(Listing listing) {
        listingsBySeller.computeIfAbsent(listing.sellerUuid(), k -> new LinkedHashSet<>()).add(listing.listingId());
        listingsByMarketKey.computeIfAbsent(listing.marketKey(), k -> new LinkedHashSet<>()).add(listing.listingId());
        listingsByCategory.computeIfAbsent(listing.categoryId(), k -> new LinkedHashSet<>()).add(listing.listingId());
    }

    private void indexRemove(Listing listing) {
        UUID id = listing.listingId();
        Set<UUID> bySellerIds = listingsBySeller.get(listing.sellerUuid());
        if (bySellerIds != null) {
            bySellerIds.remove(id);
            if (bySellerIds.isEmpty()) listingsBySeller.remove(listing.sellerUuid());
        }
        Set<UUID> byKeyIds = listingsByMarketKey.get(listing.marketKey());
        if (byKeyIds != null) {
            byKeyIds.remove(id);
            if (byKeyIds.isEmpty()) listingsByMarketKey.remove(listing.marketKey());
        }
        Set<UUID> byCatIds = listingsByCategory.get(listing.categoryId());
        if (byCatIds != null) {
            byCatIds.remove(id);
            if (byCatIds.isEmpty()) listingsByCategory.remove(listing.categoryId());
        }
    }

    private Collection<String> stringifyIds(Collection<UUID> ids) {
        List<String> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            result.add(id.toString());
        }
        return result;
    }

    private Map<String, String> stringifyUpserts(Map<UUID, String> upsertsById) {
        Map<String, String> result = new LinkedHashMap<>(upsertsById.size());
        for (Map.Entry<UUID, String> entry : upsertsById.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private Comparator<Listing> listingComparator(SortMode sortMode) {
        return switch (sortMode) {
            case NEWEST_FIRST -> Comparator.comparingLong(Listing::listedAtEpochMillis).reversed();
            case OLDEST_FIRST -> Comparator.comparingLong(Listing::listedAtEpochMillis);
            case LOWEST_TO_HIGHEST -> Comparator.comparingLong(Listing::unitPrice)
                    .thenComparing(Comparator.comparingLong(Listing::listedAtEpochMillis).reversed());
            case HIGHEST_TO_LOWEST -> Comparator.comparingLong(Listing::unitPrice).reversed()
                    .thenComparing(Comparator.comparingLong(Listing::listedAtEpochMillis).reversed());
        };
    }

    private boolean itemsSimilarIgnoringAmount(ItemStack left, ItemStack right) {
        ItemStack leftCopy = left.clone();
        ItemStack rightCopy = right.clone();
        leftCopy.setAmount(1);
        rightCopy.setAmount(1);
        return leftCopy.isSimilar(rightCopy);
    }

    private List<Listing> page(List<Listing> input, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= input.size()) {
            return List.of();
        }
        int end = Math.min(input.size(), start + pageSize);
        return List.copyOf(input.subList(start, end));
    }

    private String encode(Listing listing) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeUuid(output, listing.listingId());
            SQLiteRecordCodecSupport.writeUuid(output, listing.sellerUuid());
            SQLiteRecordCodecSupport.writeItemStack(output, listing.listedItemSnapshot());
            output.writeInt(listing.amount());
            SQLiteRecordCodecSupport.writeString(output, listing.marketKey());
            SQLiteRecordCodecSupport.writeString(output, listing.marketDisplayName());
            SQLiteRecordCodecSupport.writeString(output, listing.categoryId());
            output.writeLong(listing.unitPrice());
            output.writeLong(listing.listedAtEpochMillis());
            output.writeLong(listing.listingDurationMillis());
        });
    }

    private Listing decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new Listing(
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readUuid(input),
                SQLiteRecordCodecSupport.readItemStack(input),
                input.readInt(),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                SQLiteRecordCodecSupport.readString(input),
                input.readLong(),
                input.readLong(),
                input.readLong()
        ));
    }

    private record ListingWriteBatch(Map<UUID, String> upsertsById, Set<UUID> deleteIds) {
        private static ListingWriteBatch empty() {
            return new ListingWriteBatch(Map.of(), Set.of());
        }

        private boolean isEmpty() {
            return upsertsById.isEmpty() && deleteIds.isEmpty();
        }
    }
}
