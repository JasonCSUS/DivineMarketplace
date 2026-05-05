package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteSalesStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Preloaded in-memory sale-history index backed by SQLite sales storage.
 *
 * SQLite remains the durable source of truth. This index is a read-optimized
 * projection used by commands, GUI pages, and recommendation lookups so common
 * market-history reads do not scan the sales table on every click.
 *
 * Enchanted-book lookup rule:
 * - normal items use exact market-key history
 * - single-enchant book history also includes mixed books containing the same
 *   enchant+level component
 * - mixed-book history includes the exact mixed key plus matching single-enchant
 *   component histories
 */
public final class InMemorySaleHistoryIndex {
    private static final String BOOK_PREFIX = "enchanted_book:";
    private static final String MIXED_BOOK_PREFIX = "enchanted_book:mixed|";

    private final SQLiteSalesStore salesStore;
    private final Map<String, List<SaleRecord>> byMarketKey = new LinkedHashMap<>();

    public InMemorySaleHistoryIndex(SQLiteSalesStore salesStore) {
        this.salesStore = Objects.requireNonNull(salesStore, "salesStore");
        reload();
    }

    public synchronized void reload() {
        byMarketKey.clear();
        for (SaleRecord record : salesStore.getAllSales()) {
            byMarketKey.computeIfAbsent(record.marketKey(), ignored -> new ArrayList<>()).add(record);
        }
        for (List<SaleRecord> records : byMarketKey.values()) {
            records.sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis));
        }
    }

    public synchronized void recordSale(SaleRecord saleRecord) {
        byMarketKey.computeIfAbsent(saleRecord.marketKey(), ignored -> new ArrayList<>()).add(saleRecord);
        byMarketKey.get(saleRecord.marketKey()).sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis));
    }

    public synchronized List<SaleRecord> getSaleHistoryForMarketKey(String marketKey, int page, int pageSize) {
        List<SaleRecord> all = matchingSaleHistory(marketKey);
        all.sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis).reversed());

        int safePageSize = Math.max(1, pageSize);
        int start = Math.max(0, page) * safePageSize;
        if (start >= all.size()) {
            return List.of();
        }
        int end = Math.min(all.size(), start + safePageSize);
        return List.copyOf(all.subList(start, end));
    }

    public synchronized List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis) {
        long cutoff = System.currentTimeMillis() - lookbackMillis;
        return byMarketKey.getOrDefault(marketKey, List.of()).stream()
                .filter(record -> record.soldAtEpochMillis() >= cutoff)
                .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis))
                .toList();
    }

    private List<SaleRecord> matchingSaleHistory(String marketKey) {
        if (marketKey == null || marketKey.isBlank()) {
            return List.of();
        }
        if (!marketKey.startsWith(BOOK_PREFIX)) {
            return new ArrayList<>(byMarketKey.getOrDefault(marketKey, List.of()));
        }

        Set<String> matchingKeys = matchingEnchantedBookMarketKeys(marketKey);
        List<SaleRecord> records = new ArrayList<>();
        for (String matchingKey : matchingKeys) {
            records.addAll(byMarketKey.getOrDefault(matchingKey, List.of()));
        }
        return records;
    }

    private Set<String> matchingEnchantedBookMarketKeys(String marketKey) {
        Set<String> matchingKeys = new LinkedHashSet<>();
        matchingKeys.add(marketKey);

        if (marketKey.startsWith(MIXED_BOOK_PREFIX)) {
            for (String component : componentTokensFromMixedKey(marketKey)) {
                matchingKeys.add(BOOK_PREFIX + component);
            }
            return matchingKeys;
        }

        String component = marketKey.substring(BOOK_PREFIX.length());
        for (String knownKey : byMarketKey.keySet()) {
            if (knownKey.startsWith(MIXED_BOOK_PREFIX) && componentTokensFromMixedKey(knownKey).contains(component)) {
                matchingKeys.add(knownKey);
            }
        }
        return matchingKeys;
    }

    private Set<String> componentTokensFromMixedKey(String marketKey) {
        if (marketKey == null || !marketKey.startsWith(MIXED_BOOK_PREFIX)) {
            return Set.of();
        }

        Set<String> components = new LinkedHashSet<>();
        String[] parts = marketKey.substring(MIXED_BOOK_PREFIX.length()).split("\\|");
        for (String part : parts) {
            if (isKeyLevelComponent(part)) {
                components.add(part);
            }
        }
        return components;
    }

    private boolean isKeyLevelComponent(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int lastColon = token.lastIndexOf(':');
        if (lastColon <= 0 || lastColon >= token.length() - 1) {
            return false;
        }
        for (int i = lastColon + 1; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
