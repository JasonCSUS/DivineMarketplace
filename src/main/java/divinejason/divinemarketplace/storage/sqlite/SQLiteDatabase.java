package divinejason.divinemarketplace.storage.sqlite;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Shared SQLite database handle backed by HikariCP.
 */
public final class SQLiteDatabase implements AutoCloseable {
    private static final int POOL_SIZE = 4;

    private final String name;
    private final HikariDataSource dataSource;
    private final ExecutorService writeExecutor;

    private SQLiteDatabase(String name, HikariDataSource dataSource, ExecutorService writeExecutor) {
        this.name = name;
        this.dataSource = dataSource;
        this.writeExecutor = writeExecutor;
    }

    public static SQLiteDatabase open(String name, Path file) {
        requireNonBlank(name, "name");
        Objects.requireNonNull(file, "file");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(1);
        config.setPoolName("sqlite-" + safeName(name));
        config.setConnectionInitSql(
                "PRAGMA foreign_keys = ON; " +
                "PRAGMA journal_mode = WAL; " +
                "PRAGMA synchronous = NORMAL; " +
                "PRAGMA temp_store = MEMORY; " +
                "PRAGMA busy_timeout = 5000;"
        );

        HikariDataSource dataSource = new HikariDataSource(config);

        ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqlite-write-" + safeName(name));
            t.setDaemon(true);
            return t;
        });

        return new SQLiteDatabase(name, dataSource, writeExecutor);
    }

    public SQLiteStore store(String modulePrefix) {
        return new SQLiteStore(this, modulePrefix);
    }

    public String getName() {
        return name;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public ExecutorService getWriteExecutor() {
        return writeExecutor;
    }

    @Override
    public void close() {
        writeExecutor.shutdown();
        dataSource.close();
    }

    private static String safeName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank.");
        }
        return value;
    }
}
