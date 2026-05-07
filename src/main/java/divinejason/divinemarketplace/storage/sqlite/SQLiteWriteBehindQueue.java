package divinejason.divinemarketplace.storage.sqlite;

/*
 * Layer : storage/sqlite/core
 * Owns  : the single queued write-behind path for SQLite durability
 * Calls : SQLiteStore.applyBatchAsync/applyBatch only
 * Avoids: Bukkit API, service decisions, and memory mutation rules
 */

import divinejason.divinemarketplace.util.PerfTimer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Memory-first write-behind queue for SQLite mutations.
 *
 * <p>Services/stores update their in-memory state first, then enqueue the SQL
 * mutations that make that committed memory state durable.  The queue drains on
 * a timer, by threshold, and during plugin shutdown.  All flushed mutations are
 * applied through {@link SQLiteStore#applyBatch(SQLiteWriteBatch)}, so each drain
 * is one SQLite transaction on the shared single write executor.</p>
 */
public final class SQLiteWriteBehindQueue {
    private static final int AUTO_FLUSH_MUTATION_THRESHOLD = 500;

    private final SQLiteStore sqliteStore;
    private final Logger logger;
    private final Object lock = new Object();
    private final List<SQLiteMutation> pendingMutations = new ArrayList<>();
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final Semaphore storageAccess = new Semaphore(1, true);
    private volatile CompletableFuture<Void> activeFlush = CompletableFuture.completedFuture(null);
    private volatile boolean acceptingWrites = true;
    private volatile boolean maintenanceInProgress = false;
    private volatile Consumer<Throwable> flushFailureHandler;

    public SQLiteWriteBehindQueue(SQLiteStore sqliteStore, Logger logger) {
        this.sqliteStore = Objects.requireNonNull(sqliteStore, "sqliteStore");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Enqueues one batch for a future drain.  Does not block on SQLite. */
    public void enqueue(SQLiteWriteBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.isEmpty()) {
            return;
        }

        boolean shouldFlush;
        synchronized (lock) {
            if (!acceptingWrites) {
                throw new IllegalStateException("SQLite write-behind queue is shutting down.");
            }
            pendingMutations.addAll(batch.mutations());
            shouldFlush = pendingMutations.size() >= AUTO_FLUSH_MUTATION_THRESHOLD;
        }

        if (shouldFlush) {
            flushAsync();
        }
    }

    public int pendingMutationCount() {
        synchronized (lock) {
            return pendingMutations.size();
        }
    }

    /** Registers an optional callback used by the runtime to enter storage recovery after a failed async flush. */
    public void setFlushFailureHandler(Consumer<Throwable> flushFailureHandler) {
        this.flushFailureHandler = flushFailureHandler;
    }

    /** Starts one async flush if no flush is already running or maintenance is active. */
    public void flushAsync() {
        if (!storageAccess.tryAcquire()) {
            return;
        }

        SQLiteWriteBatch batch;
        synchronized (lock) {
            if (flushInProgress.get() || maintenanceInProgress) {
                storageAccess.release();
                return;
            }
            batch = drainLocked("periodic write-behind flush");
            if (batch.isEmpty()) {
                storageAccess.release();
                return;
            }
            flushInProgress.set(true);
        }

        CompletableFuture<Void> tracked = sqliteStore.applyBatchAsync(batch)
            .thenAccept(ignored -> { })
            .whenComplete((ignored, throwable) -> {
                boolean shouldFlushAgain;
                Throwable failure = null;
                try {
                    if (throwable != null) {
                        failure = unwrapCompletionException(throwable);
                        requeueFront(batch);
                        logger.log(Level.SEVERE,
                            "Failed to flush SQLite write-behind queue. Mutations were re-queued.",
                            failure);
                        shouldFlushAgain = false;
                    } else {
                        synchronized (lock) {
                            shouldFlushAgain = pendingMutations.size() >= AUTO_FLUSH_MUTATION_THRESHOLD && !maintenanceInProgress;
                        }
                    }
                } finally {
                    flushInProgress.set(false);
                    activeFlush = CompletableFuture.completedFuture(null);
                    storageAccess.release();
                }

                if (failure != null) {
                    Consumer<Throwable> handler = flushFailureHandler;
                    if (handler != null) {
                        handler.accept(failure);
                    }
                }

                if (shouldFlushAgain) {
                    flushAsync();
                }
            });
        activeFlush = tracked;
    }

    /** Drains all pending mutations synchronously.  Use from plugin disable or before maintenance. */
    public void flushBlocking() {
        waitForActiveFlushToFinish();
        storageAccess.acquireUninterruptibly();
        try {
            flushBlockingWithStorageAccess("blocking write-behind flush");
        } finally {
            storageAccess.release();
        }
    }

    /**
     * Runs destructive maintenance while the normal writer is idle.
     *
     * <p>New runtime actions may still enqueue mutations while maintenance is
     * running, but the queue will not flush them until this method releases the
     * storage gate.  This keeps purge/delete/VACUUM mutually exclusive with the
     * normal writer without making player actions throw storage-queue errors.</p>
     */
    public <T> T runExclusiveMaintenance(Callable<T> maintenanceTask) {
        Objects.requireNonNull(maintenanceTask, "maintenanceTask");
        synchronized (lock) {
            maintenanceInProgress = true;
        }

        waitForActiveFlushToFinish();
        storageAccess.acquireUninterruptibly();
        try {
            flushBlockingWithStorageAccess("pre-maintenance write-behind flush");
            try {
                return maintenanceTask.call();
            } catch (Exception exception) {
                throw new IllegalStateException("SQLite maintenance task failed.", exception);
            }
        } finally {
            storageAccess.release();
            synchronized (lock) {
                maintenanceInProgress = false;
            }
            flushAsync();
        }
    }

    /** Stops accepting new writes and attempts to make all queued work durable. */
    public void shutdownAndFlush() {
        synchronized (lock) {
            acceptingWrites = false;
        }
        flushBlocking();
    }


    private void flushBlockingWithStorageAccess(String reason) {
        boolean perf = PerfTimer.enabled();
        long startNanos = perf ? System.nanoTime() : 0;
        int totalMutations = 0;
        int batches = 0;

        while (true) {
            SQLiteWriteBatch batch;
            synchronized (lock) {
                batch = drainLocked(reason);
            }

            if (batch.isEmpty()) {
                break;
            }

            if (perf) {
                batches++;
                totalMutations += batch.mutations().size();
            }
            try {
                sqliteStore.applyBatch(batch);
            } catch (SQLException exception) {
                requeueFront(batch);
                throw new IllegalStateException("Failed to flush SQLite write-behind queue.", exception);
            }
        }

        if (perf && totalMutations > 0) {
            logger.info("[DivineMarketplace][perf] write flush batches=" + batches
                    + " mutations=" + totalMutations
                    + " time=" + (System.nanoTime() - startNanos) / 1_000_000 + "ms");
        }
    }

    private SQLiteWriteBatch drainLocked(String reason) {
        if (pendingMutations.isEmpty()) {
            return SQLiteWriteBatch.builder(reason).build();
        }
        List<SQLiteMutation> drained = new ArrayList<>(pendingMutations);
        pendingMutations.clear();
        return SQLiteWriteBatch.of(reason, drained);
    }

    private void requeueFront(SQLiteWriteBatch batch) {
        synchronized (lock) {
            List<SQLiteMutation> restored = new ArrayList<>(batch.mutations().size() + pendingMutations.size());
            restored.addAll(batch.mutations());
            restored.addAll(pendingMutations);
            pendingMutations.clear();
            pendingMutations.addAll(restored);
        }
    }

    private void waitForActiveFlushToFinish() {
        CompletableFuture<Void> flush = activeFlush;
        try {
            flush.join();
        } catch (CompletionException exception) {
            logger.log(Level.WARNING,
                "Previous SQLite write-behind flush failed before blocking flush.",
                unwrapCompletionException(exception));
        }
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
