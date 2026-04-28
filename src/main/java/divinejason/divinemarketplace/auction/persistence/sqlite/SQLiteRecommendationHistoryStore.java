package divinejason.divinemarketplace.auction.persistence.sqlite;

import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.storage.sqlite.SQLiteStore;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite-backed compact daily price history store.
 *
 * One record per market key per day.
 */
public final class SQLiteRecommendationHistoryStore {
    private static final String TABLE = "price_history";

    private final SQLiteStore sqliteStore;
    private final Map<String, List<RecommendationHistoryPoint>> byMarketKey = new LinkedHashMap<>();
    private final Object lock = new Object();

    public SQLiteRecommendationHistoryStore(SQLiteStore sqliteStore) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        try {
            sqliteStore.ensureTable(TABLE);
            reload();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite price history table.", exception);
        }
    }

    public void reload() {
        synchronized (lock) {
            byMarketKey.clear();
            try {
                for (String encoded : sqliteStore.getAll(TABLE).values()) {
                    RecommendationHistoryPoint point = decode(encoded);
                    byMarketKey.computeIfAbsent(point.marketKey(), ignored -> new ArrayList<>()).add(point);
                }
                for (List<RecommendationHistoryPoint> points : byMarketKey.values()) {
                    points.sort(Comparator.comparingLong(RecommendationHistoryPoint::recordedAtEpochMillis));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to reload price history from SQLite.", exception);
            }
        }
    }

    public void upsertDailyPoint(String marketKey, long recommendedUnitPrice, long recordedAtEpochMillis) {
        String id = dailyId(marketKey, recordedAtEpochMillis);
        RecommendationHistoryPoint point = new RecommendationHistoryPoint(marketKey, recommendedUnitPrice, recordedAtEpochMillis);

        try {
            sqliteStore.put(TABLE, id, encode(point));
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to upsert price history point in SQLite.", exception);
        }

        reload();
    }

    public List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month) {
        synchronized (lock) {
            return byMarketKey.getOrDefault(marketKey, List.of()).stream()
                    .filter(point -> YearMonth.from(toLocalDate(point.recordedAtEpochMillis())).equals(month))
                    .sorted(Comparator.comparingLong(RecommendationHistoryPoint::recordedAtEpochMillis))
                    .toList();
        }
    }

    private String dailyId(String marketKey, long recordedAtEpochMillis) {
        return marketKey + "|" + toLocalDate(recordedAtEpochMillis);
    }

    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String encode(RecommendationHistoryPoint point) {
        return SQLiteRecordCodecSupport.encode(output -> {
            SQLiteRecordCodecSupport.writeString(output, point.marketKey());
            output.writeLong(point.recommendedUnitPrice());
            output.writeLong(point.recordedAtEpochMillis());
        });
    }

    private RecommendationHistoryPoint decode(String value) {
        return SQLiteRecordCodecSupport.decode(value, input -> new RecommendationHistoryPoint(
                SQLiteRecordCodecSupport.readString(input),
                input.readLong(),
                input.readLong()
        ));
    }
}
