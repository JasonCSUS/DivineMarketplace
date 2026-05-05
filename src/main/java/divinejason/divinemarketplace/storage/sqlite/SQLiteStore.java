package divinejason.divinemarketplace.storage.sqlite;

import java.sql.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * Namespaced key-value store backed by SQLiteDatabase.
 */
public final class SQLiteStore {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SQLiteDatabase database;
    private final String prefix;

    SQLiteStore(SQLiteDatabase database, String prefix) {
        this.database = Objects.requireNonNull(database, "database");
        this.prefix = safeIdentifier(prefix, "prefix");
    }

    public void ensureTable(String tableName) throws SQLException {
        String table = qualifiedTable(tableName);
        String ddl = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                "  id         VARCHAR(128) PRIMARY KEY," +
                "  value      TEXT         NOT NULL," +
                "  updated_at BIGINT       NOT NULL" +
                ");";

        try (Connection conn = connection(); Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }

    public Optional<String> get(String tableName, String id) throws SQLException {
        String table = qualifiedTable(tableName);
        String safeId = SQLiteDatabase.requireNonBlank(id, "id");
        ensureTable(tableName);

        String sql = "SELECT value FROM " + table + " WHERE id = ? LIMIT 1;";
        try (Connection conn = connection(); PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, safeId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String value = rs.getString("value");
                return Optional.ofNullable(value).filter(v -> !v.isBlank());
            }
        }
    }

    public Map<String, String> getAll(String tableName) throws SQLException {
        String table = qualifiedTable(tableName);
        ensureTable(tableName);

        String sql = "SELECT id, value FROM " + table + ";";
        try (Connection conn = connection();
             PreparedStatement st = conn.prepareStatement(sql);
             ResultSet rs = st.executeQuery()) {
            Map<String, String> result = new LinkedHashMap<>();
            while (rs.next()) {
                String value = rs.getString("value");
                if (value != null && !value.isBlank()) {
                    result.put(rs.getString("id"), value);
                }
            }
            return Collections.unmodifiableMap(result);
        }
    }

    public boolean exists(String tableName, String id) throws SQLException {
        String table = qualifiedTable(tableName);
        String safeId = SQLiteDatabase.requireNonBlank(id, "id");
        ensureTable(tableName);

        String sql = "SELECT 1 FROM " + table + " WHERE id = ? LIMIT 1;";
        try (Connection conn = connection(); PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, safeId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void put(String tableName, String id, String value) throws SQLException {
        String table = qualifiedTable(tableName);
        String safeId = SQLiteDatabase.requireNonBlank(id, "id");
        String safeValue = SQLiteDatabase.requireNonBlank(value, "value");
        ensureTable(tableName);

        try (Connection conn = connection(); PreparedStatement st = conn.prepareStatement(upsertSql(table))) {
            st.setString(1, safeId);
            st.setString(2, safeValue);
            st.setLong(3, epochNow());
            st.executeUpdate();
        }
    }

    public int putBatch(String tableName, Map<String, String> values) throws SQLException {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) return 0;

        String table = qualifiedTable(tableName);
        ensureTable(tableName);

        try (Connection conn = connection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement st = conn.prepareStatement(upsertSql(table))) {
                long now = epochNow();
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;

                    st.setString(1, entry.getKey());
                    st.setString(2, entry.getValue());
                    st.setLong(3, now);
                    st.addBatch();
                }

                int total = 0;
                for (int r : st.executeBatch()) {
                    if (r > 0) total += r;
                }

                conn.commit();
                return total;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public int replaceAll(String tableName, Map<String, String> values) throws SQLException {
        Objects.requireNonNull(values, "values");
        String table = qualifiedTable(tableName);
        ensureTable(tableName);

        try (Connection conn = connection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (Statement deleteStatement = conn.createStatement();
                 PreparedStatement upsert = conn.prepareStatement(upsertSql(table))) {
                deleteStatement.executeUpdate("DELETE FROM " + table + ";");

                long now = epochNow();
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;

                    upsert.setString(1, entry.getKey());
                    upsert.setString(2, entry.getValue());
                    upsert.setLong(3, now);
                    upsert.addBatch();
                }

                upsert.executeBatch();
                conn.commit();
                return values.size();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public boolean delete(String tableName, String id) throws SQLException {
        String table = qualifiedTable(tableName);
        String safeId = SQLiteDatabase.requireNonBlank(id, "id");
        ensureTable(tableName);

        String sql = "DELETE FROM " + table + " WHERE id = ?;";
        try (Connection conn = connection(); PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, safeId);
            return st.executeUpdate() > 0;
        }
    }

    public int deleteBatch(String tableName, Collection<String> ids) throws SQLException {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) return 0;

        String table = qualifiedTable(tableName);
        ensureTable(tableName);

        String sql = "DELETE FROM " + table + " WHERE id = ?;";
        try (Connection conn = connection()) {
            boolean prevAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement st = conn.prepareStatement(sql)) {
                for (String id : ids) {
                    if (id == null || id.isBlank()) continue;
                    st.setString(1, id);
                    st.addBatch();
                }

                int total = 0;
                for (int r : st.executeBatch()) {
                    if (r > 0) total += r;
                }

                conn.commit();
                return total;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(prevAutoCommit);
            }
        }
    }

    public CompletableFuture<Void> putAsync(String tableName, String id, String value) {
        return CompletableFuture.runAsync(() -> {
            try {
                put(tableName, id, value);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, database.getWriteExecutor());
    }

    public CompletableFuture<Integer> putBatchAsync(String tableName, Map<String, String> values) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return putBatch(tableName, values);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, database.getWriteExecutor());
    }

    public CompletableFuture<Integer> deleteBatchAsync(String tableName, Collection<String> ids) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return deleteBatch(tableName, ids);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, database.getWriteExecutor());
    }

    public CompletableFuture<Integer> replaceAllAsync(String tableName, Map<String, String> values) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return replaceAll(tableName, values);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, database.getWriteExecutor());
    }


    public long databaseFileSizeBytes() {
        try {
            return Files.exists(database.getFile()) ? Files.size(database.getFile()) : 0L;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read SQLite database file size.", exception);
        }
    }

    private Connection connection() throws SQLException {
        return database.getDataSource().getConnection();
    }

    private String qualifiedTable(String tableName) {
        return prefix + "_" + safeIdentifier(tableName, "tableName");
    }

    private static String upsertSql(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " (id, value, updated_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at;";
    }

    private static long epochNow() {
        return Instant.now().getEpochSecond();
    }

    private static String safeIdentifier(String value, String field) {
        SQLiteDatabase.requireNonBlank(value, field);
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must match [A-Za-z_][A-Za-z0-9_]*");
        }
        return value;
    }
}
