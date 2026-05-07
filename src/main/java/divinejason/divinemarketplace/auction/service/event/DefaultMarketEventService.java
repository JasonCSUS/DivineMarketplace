package divinejason.divinemarketplace.auction.service.event;

/*
 * Layer : service
 * Owns  : canonical market event write/read coordination and in-memory index
 * Calls : SQLiteMarketEventStore (store layer) only — never GUI or commands
 */


/*
 * File role: Implements canonical market event service with in-memory index projection, enchanted-book cross-key logic, and sale/admin history projections.
 */
import divinejason.divinemarketplace.auction.model.MarketEventRecord;
import divinejason.divinemarketplace.auction.model.MarketEventType;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteMarketEventStore;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Concrete market event service with preloaded in-memory indexes.
 *
 * Single canonical write target for all market actions.
 * Player sale history and admin audit history are projections over the event index.
 *
 * Enchanted-book cross-key rule:
 * - single-enchant book keys match mixed-book events containing that component.
 * - mixed-book keys match their own events plus each component single-enchant event.
 *
 * Threading: all public methods synchronize on {@code lock}.
 */
public final class DefaultMarketEventService implements MarketEventService {
    private static final String BOOK_PREFIX = "enchanted_book:";
    private static final String MIXED_BOOK_PREFIX = "enchanted_book:mixed|";

    private final SQLiteMarketEventStore store;

    private final Map<String, MarketEventRecord> indexById = new LinkedHashMap<>();
    private final Map<String, List<MarketEventRecord>> byMarketKey = new LinkedHashMap<>();
    private final Map<UUID, List<MarketEventRecord>> byPlayer = new LinkedHashMap<>();
    private final Map<MarketEventType, List<MarketEventRecord>> byType = new LinkedHashMap<>();
    private final Map<UUID, Map<MarketEventType, List<MarketEventRecord>>> byPlayerAndType = new LinkedHashMap<>();
    private final Object lock = new Object();

    public DefaultMarketEventService(SQLiteMarketEventStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    // -------------------------------------------------------------------------
    // Write helpers
    // -------------------------------------------------------------------------

    @Override
    public void appendInMemory(MarketEventRecord event) {
        Objects.requireNonNull(event, "event");
        store.appendInMemory(event);
        synchronized (lock) {
            indexById.put(event.eventId(), event);
            if (event.marketKey() != null) {
                byMarketKey.computeIfAbsent(event.marketKey(), k -> new ArrayList<>()).add(event);
            }
            byType.computeIfAbsent(event.eventType(), k -> new ArrayList<>()).add(event);
            for (UUID uuid : playerUuids(event)) {
                byPlayer.computeIfAbsent(uuid, k -> new ArrayList<>()).add(event);
                byPlayerAndType.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .computeIfAbsent(event.eventType(), k -> new ArrayList<>()).add(event);
            }
        }
    }

    @Override
    public void deleteInMemory(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        store.deleteInMemory(eventId);
        synchronized (lock) {
            MarketEventRecord event = indexById.remove(eventId);
            if (event == null) return;
            if (event.marketKey() != null) {
                List<MarketEventRecord> keyed = byMarketKey.get(event.marketKey());
                if (keyed != null) {
                    keyed.remove(event);
                    if (keyed.isEmpty()) byMarketKey.remove(event.marketKey());
                }
            }
            List<MarketEventRecord> byTyped = byType.get(event.eventType());
            if (byTyped != null) {
                byTyped.remove(event);
                if (byTyped.isEmpty()) byType.remove(event.eventType());
            }
            for (UUID uuid : playerUuids(event)) {
                List<MarketEventRecord> owned = byPlayer.get(uuid);
                if (owned != null) {
                    owned.remove(event);
                    if (owned.isEmpty()) byPlayer.remove(uuid);
                }
                Map<MarketEventType, List<MarketEventRecord>> playerTypeMap = byPlayerAndType.get(uuid);
                if (playerTypeMap != null) {
                    List<MarketEventRecord> playerTyped = playerTypeMap.get(event.eventType());
                    if (playerTyped != null) {
                        playerTyped.remove(event);
                        if (playerTyped.isEmpty()) playerTypeMap.remove(event.eventType());
                    }
                    if (playerTypeMap.isEmpty()) byPlayerAndType.remove(uuid);
                }
            }
        }
    }

    @Override
    public SQLiteMutation putMutation(MarketEventRecord event) {
        return store.putMutation(event);
    }

    @Override
    public SQLiteMutation deleteMutation(String eventId) {
        return store.deleteMutation(eventId);
    }

    // -------------------------------------------------------------------------
    // Player-facing sale history (BUY events with non-null itemSnapshot)
    // -------------------------------------------------------------------------

    @Override
    public List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis) {
        long cutoff = System.currentTimeMillis() - lookbackMillis;
        synchronized (lock) {
            return matchingBuyEventsForHistory(marketKey).stream()
                    .filter(e -> e.timestampEpochMillis() >= cutoff)
                    .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis))
                    .map(this::toSaleRecord)
                    .toList();
        }
    }

    @Override
    public List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize) {
        synchronized (lock) {
            List<SaleRecord> records = matchingBuyEventsForHistory(marketKey).stream()
                    .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                    .map(this::toSaleRecord)
                    .toList();
            return page(records, page, pageSize);
        }
    }

    // -------------------------------------------------------------------------
    // Admin / raw event queries
    // -------------------------------------------------------------------------

    @Override
    public List<MarketEventRecord> findByType(MarketEventType type, int page, int pageSize) {
        synchronized (lock) {
            return page(
                    byType.getOrDefault(type, List.of()).stream()
                            .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                            .toList(),
                    page, pageSize);
        }
    }

    @Override
    public List<MarketEventRecord> findByPlayer(UUID playerUuid, int page, int pageSize) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        synchronized (lock) {
            return page(
                    byPlayer.getOrDefault(playerUuid, List.of()).stream()
                            .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                            .toList(),
                    page, pageSize);
        }
    }

    @Override
    public List<MarketEventRecord> findByPlayerAndType(UUID playerUuid, MarketEventType type, int page, int pageSize) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        synchronized (lock) {
            Map<MarketEventType, List<MarketEventRecord>> playerTypeMap = byPlayerAndType.get(playerUuid);
            List<MarketEventRecord> typed = playerTypeMap != null
                    ? playerTypeMap.getOrDefault(type, List.of())
                    : List.of();
            return page(
                    typed.stream()
                            .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                            .toList(),
                    page, pageSize);
        }
    }

    @Override
    public List<MarketEventRecord> findByPlayerAndTypes(UUID playerUuid, Collection<MarketEventType> types, int page, int pageSize) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(types, "types");
        synchronized (lock) {
            Map<MarketEventType, List<MarketEventRecord>> playerTypeMap = byPlayerAndType.get(playerUuid);
            if (playerTypeMap == null || playerTypeMap.isEmpty()) return List.of();
            List<MarketEventRecord> combined = new ArrayList<>();
            for (MarketEventType type : types) {
                List<MarketEventRecord> typed = playerTypeMap.get(type);
                if (typed != null) combined.addAll(typed);
            }
            combined.sort(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed());
            return page(combined, page, pageSize);
        }
    }

    @Override
    public List<MarketEventRecord> findByDateRange(long startEpochMillis, long endEpochMillis, int page, int pageSize) {
        synchronized (lock) {
            return page(
                    indexById.values().stream()
                            .filter(e -> e.timestampEpochMillis() >= startEpochMillis
                                    && e.timestampEpochMillis() <= endEpochMillis)
                            .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                            .toList(),
                    page, pageSize);
        }
    }

    @Override
    public List<MarketEventRecord> findByMarketKey(String marketKey, int page, int pageSize) {
        Objects.requireNonNull(marketKey, "marketKey");
        synchronized (lock) {
            return page(
                    byMarketKey.getOrDefault(marketKey, List.of()).stream()
                            .sorted(Comparator.comparingLong(MarketEventRecord::timestampEpochMillis).reversed())
                            .toList(),
                    page, pageSize);
        }
    }

    @Override
    public Optional<MarketEventRecord> findById(String eventId) {
        if (eventId == null || eventId.isBlank()) return Optional.empty();
        synchronized (lock) {
            return Optional.ofNullable(indexById.get(eventId));
        }
    }

    // -------------------------------------------------------------------------
    // Maintenance
    // -------------------------------------------------------------------------

    @Override
    public int maintenancePurgeEventsOlderThan(long cutoffEpochMillis) {
        int purged = store.maintenancePurgeEventsOlderThan(cutoffEpochMillis);
        if (purged > 0) reload();
        return purged;
    }

    @Override
    public int countAll() {
        synchronized (lock) {
            return indexById.size();
        }
    }

    @Override
    public long estimatedPayloadBytes() {
        return store.estimatedPayloadBytes();
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void loadFromStorage() {
        store.loadFromStorage();
        synchronized (lock) {
            rebuildIndexes();
        }
    }

    @Override
    public void reload() {
        store.reload();
        synchronized (lock) {
            rebuildIndexes();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void rebuildIndexes() {
        indexById.clear();
        byMarketKey.clear();
        byPlayer.clear();
        byType.clear();
        byPlayerAndType.clear();
        for (MarketEventRecord event : store.getAllEvents()) {
            indexById.put(event.eventId(), event);
            if (event.marketKey() != null) {
                byMarketKey.computeIfAbsent(event.marketKey(), k -> new ArrayList<>()).add(event);
            }
            byType.computeIfAbsent(event.eventType(), k -> new ArrayList<>()).add(event);
            for (UUID uuid : playerUuids(event)) {
                byPlayer.computeIfAbsent(uuid, k -> new ArrayList<>()).add(event);
                byPlayerAndType.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
                        .computeIfAbsent(event.eventType(), k -> new ArrayList<>()).add(event);
            }
        }
    }

    /** BUY events with non-null itemSnapshot, applying enchanted-book cross-key expansion. */
    private List<MarketEventRecord> matchingBuyEventsForHistory(String marketKey) {
        if (marketKey == null || marketKey.isBlank()) return List.of();
        Set<String> keys = matchingMarketKeys(marketKey);
        List<MarketEventRecord> result = new ArrayList<>();
        for (String key : keys) {
            for (MarketEventRecord event : byMarketKey.getOrDefault(key, List.of())) {
                if (event.eventType() == MarketEventType.BUY && event.itemSnapshot() != null) {
                    result.add(event);
                }
            }
        }
        return result;
    }

    private Set<String> matchingMarketKeys(String marketKey) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(marketKey);
        if (!marketKey.startsWith(BOOK_PREFIX)) return keys;

        if (marketKey.startsWith(MIXED_BOOK_PREFIX)) {
            for (String component : componentTokensFromMixedKey(marketKey)) {
                keys.add(BOOK_PREFIX + component);
            }
            return keys;
        }

        String component = marketKey.substring(BOOK_PREFIX.length());
        for (String known : byMarketKey.keySet()) {
            if (known.startsWith(MIXED_BOOK_PREFIX) && componentTokensFromMixedKey(known).contains(component)) {
                keys.add(known);
            }
        }
        return keys;
    }

    private Set<String> componentTokensFromMixedKey(String marketKey) {
        if (marketKey == null || !marketKey.startsWith(MIXED_BOOK_PREFIX)) return Set.of();
        Set<String> components = new LinkedHashSet<>();
        String[] parts = marketKey.substring(MIXED_BOOK_PREFIX.length()).split("\\|");
        for (String part : parts) {
            if (isKeyLevelComponent(part)) components.add(part);
        }
        return components;
    }

    private boolean isKeyLevelComponent(String token) {
        if (token == null || token.isBlank()) return false;
        int lastColon = token.lastIndexOf(':');
        if (lastColon <= 0 || lastColon >= token.length() - 1) return false;
        for (int i = lastColon + 1; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    private SaleRecord toSaleRecord(MarketEventRecord event) {
        return new SaleRecord(
                event.marketKey(),
                event.marketDisplayName(),
                event.itemSnapshot(),
                event.amount(),
                event.unitPrice(),
                event.timestampEpochMillis(),
                event.trainingParticipation()
        );
    }

    private static Set<UUID> playerUuids(MarketEventRecord event) {
        Set<UUID> uuids = new LinkedHashSet<>();
        if (event.sellerUuid() != null) uuids.add(event.sellerUuid());
        if (event.buyerUuid() != null) uuids.add(event.buyerUuid());
        if (event.ownerUuid() != null) uuids.add(event.ownerUuid());
        return uuids;
    }

    private static <T> List<T> page(List<T> input, int page, int pageSize) {
        int start = Math.max(0, page) * Math.max(1, pageSize);
        if (start >= input.size()) return List.of();
        return List.copyOf(input.subList(start, Math.min(input.size(), start + Math.max(1, pageSize))));
    }
}
