package divinejason.divinemarketplace.auction.service.purchase;


/*
 * File role: Coordinates multi-step market writes with compensating rollback actions when a subsequent step fails.
 */
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Lightweight rollback helper for multi-store market writes.
 *
 * This is not a database transaction. It is a defensive coordinator for flows
 * that touch Vault, listing storage, claim storage, and audit records in one
 * operation. Each successful step can register a compensating action. If a subsequent
 * step fails, rollback actions run in reverse order.
 */
public final class MarketWriteTransaction {
    private final Logger logger;
    private final Deque<Runnable> rollbackActions = new ArrayDeque<>();
    private boolean committed;

    public MarketWriteTransaction(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void onRollback(Runnable action) {
        if (committed) {
            throw new IllegalStateException("Cannot register rollback action after commit.");
        }
        rollbackActions.push(Objects.requireNonNull(action, "action"));
    }

    public void commit() {
        committed = true;
        rollbackActions.clear();
    }

    public void rollbackQuietly() {
        while (!rollbackActions.isEmpty()) {
            try {
                rollbackActions.pop().run();
            } catch (RuntimeException exception) {
                logger.warning("Market rollback action failed: " + exception.getMessage());
            }
        }
    }
}
