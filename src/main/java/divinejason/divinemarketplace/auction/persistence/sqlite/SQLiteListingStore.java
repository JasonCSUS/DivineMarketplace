package divinejason.divinemarketplace.auction.persistence.sqlite;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteRecordCodecSupport;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;
import org.bukkit.inventory.ItemStack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SQLite-backed listing store.
 *
 * Renamed from the transitional Binary* naming now that runtime storage is SQLite-backed.
 */
public final class SQLiteListingStore {
    private static final String TABLE = "listings";

    private final SQLiteStore sqliteStore;
    private final Map<UUID, Listing> cacheById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteListingStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite listings table.", exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            try {
                cacheById.clear();
                for (Map.Entry<String, String> entry : sqliteStore.getAll(TABLE).entrySet()) {
                    Listing listing = decode(entry.getValue());
                    cacheById.put(listing.listingId(), listing);
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload listings from SQLite.", exception);
            }
        }
    }

    public void saveOrReplace(Listing listing) {
        synchronized (lock) {
            cacheById.put(listing.listingId(), listing);
            try {
                sqliteStore.put(TABLE, listing.listingId().toString(), encode(listing));
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to write listing to SQLite.", exception);
            }
        }
    }

    public void delete(UUID listingId) {
        synchronized (lock) {
            cacheById.remove(listingId);
            try {
                sqliteStore.delete(TABLE, listingId.toString());
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to delete listing from SQLite.", exception);
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
            return cacheById.values().stream()
                    .filter(listing -> listing.sellerUuid().equals(sellerUuid))
                    .filter(listing -> listing.marketKey().equals(marketKey))
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
            List<Listing> filtered = cacheById.values().stream()
                    .filter(listing -> listing.sellerUuid().equals(sellerUuid))
                    .sorted(Comparator.comparingLong(Listing::listedAtEpochMillis).reversed())
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<Listing> findByMarketKey(String marketKey, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = cacheById.values().stream()
                    .filter(listing -> listing.marketKey().equals(marketKey))
                    .sorted(listingComparator(sortMode))
                    .toList();
            return page(filtered, page, pageSize);
        }
    }

    public List<Listing> findByCategoryId(String categoryId, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = cacheById.values().stream()
                    .filter(listing -> listing.categoryId().equals(categoryId))
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
        synchronized (lock) {
            return List.copyOf(cacheById.values());
        }
    }

    public int countBySeller(UUID sellerUuid) {
        synchronized (lock) {
            return (int) cacheById.values().stream().filter(listing -> listing.sellerUuid().equals(sellerUuid)).count();
        }
    }

    public int countByMarketKey(String marketKey) {
        synchronized (lock) {
            return (int) cacheById.values().stream().filter(listing -> listing.marketKey().equals(marketKey)).count();
        }
    }

    public int countByCategoryId(String categoryId) {
        synchronized (lock) {
            return (int) cacheById.values().stream().filter(listing -> listing.categoryId().equals(categoryId)).count();
        }
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
}
