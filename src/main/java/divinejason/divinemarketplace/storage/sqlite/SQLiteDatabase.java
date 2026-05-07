package divinejason.divinemarketplace.storage.sqlite;


/*
 * File role: Wraps the shared SQLite connection, schema initialization, transactions, WAL maintenance, and file-size reporting.
 */
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared SQLite database handle backed by HikariCP.
 */
public final class SQLiteDatabase implements AutoCloseable {
    private static final int POOL_SIZE = 4;

    private final String name;
    private final Path file;
    private volatile HikariDataSource dataSource;
    private final ExecutorService writeExecutor;

    private SQLiteDatabase(String name, Path file, HikariDataSource dataSource, ExecutorService writeExecutor) {
        this.name = name;
        this.file = file;
        this.dataSource = dataSource;
        this.writeExecutor = writeExecutor;
    }

    public static SQLiteDatabase open(String name, Path file) {
        requireNonBlank(name, "name");
        Objects.requireNonNull(file, "file");

        HikariDataSource dataSource = createDataSource(name, file);

        ExecutorService writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sqlite-write-" + safeName(name));
            t.setDaemon(true);
            return t;
        });

        return new SQLiteDatabase(name, file, dataSource, writeExecutor);
    }

    public SQLiteStore store(String modulePrefix) {
        return new SQLiteStore(this, modulePrefix);
    }

    public String getName() {
        return name;
    }

    public Path getFile() {
        return file;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Reopens the Hikari datasource while keeping the same SQLiteDatabase wrapper
     * and single write executor. Existing SQLiteStore instances keep using this
     * object, so recovery can refresh the connection without rebuilding runtime
     * memory or services.
     */
    public synchronized void reconnect() {
        HikariDataSource old = dataSource;
        if (old != null && !old.isClosed()) {
            old.close();
        }
        dataSource = createDataSource(name, file);
    }

    public ExecutorService getWriteExecutor() {
        return writeExecutor;
    }

    @Override
    public synchronized void close() {
        writeExecutor.shutdown();
        HikariDataSource current = dataSource;
        if (current != null && !current.isClosed()) {
            current.close();
        }
    }

    private static HikariDataSource createDataSource(String name, Path file) {
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
        return new HikariDataSource(config);
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
