package divinejason.divinemarketplace.auction.persistence;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.setup.PluginDirectoryLayout;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Binary persistence for active listing state.
 *
 * Current scaffolding rule:
 * - only the current listing schema is supported
 * - no backward-compat migration path is kept during active v1 scaffolding
 */
public final class BinaryListingStore {
    private static final String MAGIC = "DMLIST";
    private static final int VERSION = 2;

    private final Path filePath;
    private final Object lock = new Object();

    public BinaryListingStore(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve(PluginDirectoryLayout.DATA_LISTINGS));
    }

    public BinaryListingStore(Path filePath) {
        this.filePath = filePath;
        try {
            BinaryStoreSupport.ensureFileExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize listings file: " + filePath, exception);
        }
    }

    public void saveOrReplace(Listing listing) {
        synchronized (lock) {
            List<Listing> listings = loadAll();
            int existingIndex = indexOfListing(listings, listing.listingId());
            if (existingIndex >= 0) {
                listings.set(existingIndex, listing);
            } else {
                listings.add(listing);
            }
            saveAll(listings);
        }
    }

    public void delete(UUID listingId) {
        synchronized (lock) {
            List<Listing> listings = loadAll();
            if (listings.removeIf(listing -> listing.listingId().equals(listingId))) {
                saveAll(listings);
            }
        }
    }

    public Optional<Listing> findById(UUID listingId) {
        synchronized (lock) {
            return loadAll().stream().filter(listing -> listing.listingId().equals(listingId)).findFirst();
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
            return loadAll().stream()
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
            List<Listing> filtered = loadAll().stream()
                    .filter(listing -> listing.sellerUuid().equals(sellerUuid))
                    .sorted(Comparator.comparingLong(Listing::listedAtEpochMillis).reversed())
                    .toList();
            return BinaryStoreSupport.page(filtered, page, pageSize);
        }
    }

    public List<Listing> findByMarketKey(String marketKey, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = loadAll().stream()
                    .filter(listing -> listing.marketKey().equals(marketKey))
                    .sorted(listingComparator(sortMode))
                    .toList();
            return BinaryStoreSupport.page(filtered, page, pageSize);
        }
    }

    public List<Listing> findByCategoryId(String categoryId, SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> filtered = loadAll().stream()
                    .filter(listing -> listing.categoryId().equals(categoryId))
                    .sorted(listingComparator(sortMode))
                    .toList();
            return BinaryStoreSupport.page(filtered, page, pageSize);
        }
    }

    public List<Listing> findAll(SortMode sortMode, int page, int pageSize) {
        synchronized (lock) {
            List<Listing> all = loadAll();
            all.sort(listingComparator(sortMode));
            return BinaryStoreSupport.page(all, page, pageSize);
        }
    }

    public Iterable<Listing> getAllActive() {
        synchronized (lock) {
            return List.copyOf(loadAll());
        }
    }

    public int countBySeller(UUID sellerUuid) {
        synchronized (lock) {
            return (int) loadAll().stream().filter(listing -> listing.sellerUuid().equals(sellerUuid)).count();
        }
    }

    public int countByMarketKey(String marketKey) {
        synchronized (lock) {
            return (int) loadAll().stream().filter(listing -> listing.marketKey().equals(marketKey)).count();
        }
    }

    public int countByCategoryId(String categoryId) {
        synchronized (lock) {
            return (int) loadAll().stream().filter(listing -> listing.categoryId().equals(categoryId)).count();
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

    private int indexOfListing(List<Listing> listings, UUID listingId) {
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).listingId().equals(listingId)) {
                return i;
            }
        }
        return -1;
    }

    private List<Listing> loadAll() {
        try {
            if (BinaryStoreSupport.isEmptyFile(filePath)) {
                return new ArrayList<>();
            }

            try (DataInputStream input = BinaryStoreSupport.newInput(filePath)) {
                BinaryStoreSupport.requireHeader(input, MAGIC, VERSION);

                int count = input.readInt();
                List<Listing> listings = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    UUID listingId = BinaryStoreSupport.readUuid(input);
                    UUID sellerUuid = BinaryStoreSupport.readUuid(input);
                    ItemStack snapshot = BinaryStoreSupport.readItemStack(input);
                    int amount = input.readInt();
                    String marketKey = BinaryStoreSupport.readString(input);
                    String marketDisplayName = BinaryStoreSupport.readString(input);
                    String categoryId = BinaryStoreSupport.readString(input);
                    long unitPrice = input.readLong();
                    long listedAt = input.readLong();
                    long duration = input.readLong();

                    if (listingId != null
                            && sellerUuid != null
                            && snapshot != null
                            && amount > 0
                            && marketKey != null
                            && marketDisplayName != null
                            && categoryId != null) {
                        listings.add(new Listing(
                                listingId,
                                sellerUuid,
                                snapshot,
                                amount,
                                marketKey,
                                marketDisplayName,
                                categoryId,
                                unitPrice,
                                listedAt,
                                duration
                        ));
                    }
                }

                return listings;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read listings from " + filePath, exception);
        }
    }

    private void saveAll(List<Listing> listings) {
        try {
            BinaryStoreSupport.writeToTempFile(filePath, output -> writeListings(output, listings));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write listings to " + filePath, exception);
        }
    }

    private void writeListings(DataOutputStream output, List<Listing> listings) {
        try {
            BinaryStoreSupport.writeHeader(output, MAGIC, VERSION);
            output.writeInt(listings.size());

            for (Listing listing : listings) {
                BinaryStoreSupport.writeUuid(output, listing.listingId());
                BinaryStoreSupport.writeUuid(output, listing.sellerUuid());
                BinaryStoreSupport.writeItemStack(output, listing.listedItemSnapshot());
                output.writeInt(listing.amount());
                BinaryStoreSupport.writeString(output, listing.marketKey());
                BinaryStoreSupport.writeString(output, listing.marketDisplayName());
                BinaryStoreSupport.writeString(output, listing.categoryId());
                output.writeLong(listing.unitPrice());
                output.writeLong(listing.listedAtEpochMillis());
                output.writeLong(listing.listingDurationMillis());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed while encoding listings.", exception);
        }
    }
}
