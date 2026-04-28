package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteSalesStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Preloaded in-memory history index backed by the sales binary.
 *
 * The sales binary is read once at startup and then kept up to date as new sales
 * are recorded through MarketAnalyticsService.
 */
public final class InMemorySaleHistoryIndex {
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
        List<SaleRecord> all = new ArrayList<>(byMarketKey.getOrDefault(marketKey, List.of()));
        all.sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis).reversed());

        int start = Math.max(0, page * pageSize);
        if (start >= all.size()) {
            return List.of();
        }
        int end = Math.min(all.size(), start + pageSize);
        return List.copyOf(all.subList(start, end));
    }

    public synchronized List<SaleRecord> getRecentSalesForMarketKey(String marketKey, long lookbackMillis) {
        long cutoff = System.currentTimeMillis() - lookbackMillis;
        return byMarketKey.getOrDefault(marketKey, List.of()).stream()
                .filter(record -> record.soldAtEpochMillis() >= cutoff)
                .sorted(Comparator.comparingLong(SaleRecord::soldAtEpochMillis))
                .toList();
    }
}
