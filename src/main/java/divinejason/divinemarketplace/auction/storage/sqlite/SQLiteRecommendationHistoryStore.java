package divinejason.divinemarketplace.auction.storage.sqlite;


/*
 * Layer : storage / SQLite store
 * Owns  : one SQLite-backed table/cache boundary
 * Calls : SQLiteStore and model records only — never GUI or commands
 */

/*
 * File role: Persists and queries recommendation history records in SQLite while exposing size/retention helpers where needed.
 */
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.storage.sqlite.SQLiteMutation;
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
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite price history table.", exception);
        }
    }

    /** Initial load from SQLite into the memory cache. */
    public void loadFromStorage() {
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
                throw new IllegalStateException("Failed to load price history from SQLite.", exception);
            }
        }
    }

    public void reload() {
        loadFromStorage();
    }

    /** Updates the in-memory cache for today's price point without touching SQLite. */
    public void upsertDailyPointInMemory(String marketKey, long recommendedUnitPrice, long recordedAtEpochMillis) {
        RecommendationHistoryPoint point = new RecommendationHistoryPoint(marketKey, recommendedUnitPrice, recordedAtEpochMillis);
        LocalDate day = toLocalDate(recordedAtEpochMillis);
        synchronized (lock) {
            List<RecommendationHistoryPoint> points = byMarketKey.computeIfAbsent(marketKey, k -> new ArrayList<>());
            points.removeIf(p -> toLocalDate(p.recordedAtEpochMillis()).equals(day));
            points.add(point);
            points.sort(Comparator.comparingLong(RecommendationHistoryPoint::recordedAtEpochMillis));
        }
    }

    /** Returns a queued PUT mutation for a daily price point — does not write to SQLite. */
    public SQLiteMutation upsertDailyPointMutation(String marketKey, long recommendedUnitPrice, long recordedAtEpochMillis) {
        String id = dailyId(marketKey, recordedAtEpochMillis);
        RecommendationHistoryPoint point = new RecommendationHistoryPoint(marketKey, recommendedUnitPrice, recordedAtEpochMillis);
        return SQLiteMutation.put(TABLE, id, encode(point));
    }

    public List<RecommendationHistoryPoint> getPriceHistory(String marketKey, YearMonth month) {
        synchronized (lock) {
            return byMarketKey.getOrDefault(marketKey, List.of()).stream()
                    .filter(point -> YearMonth.from(toLocalDate(point.recordedAtEpochMillis())).equals(month))
                    .sorted(Comparator.comparingLong(RecommendationHistoryPoint::recordedAtEpochMillis))
                    .toList();
        }
    }

    public List<YearMonth> getMonthsWithData(String marketKey) {
        synchronized (lock) {
            return byMarketKey.getOrDefault(marketKey, List.of()).stream()
                    .map(point -> YearMonth.from(toLocalDate(point.recordedAtEpochMillis())))
                    .distinct()
                    .sorted(Comparator.reverseOrder())
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
