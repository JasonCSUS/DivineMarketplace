package divinejason.divinemarketplace.bootstrap;

/**
 * Lifecycle states for the DivineMarketplace runtime.
 *
 * <p>Forward-only transitions:
 * <pre>
 * STARTING → LOADING_STORAGE → READY
 *                            → FAILED
 * READY    → RECOVERING_STORAGE → READY
 *                             → FAILED
 * READY    → DISABLING
 * </pre>
 *
 * <p>Every player-facing entry point (commands, menu clicks, scheduled tasks,
 * price recalculation) must reject work unless the state is {@link #READY}.
 */
public enum MarketRuntimeState {
    /** onEnable started; storage loading has not yet begun. */
    STARTING,
    /** Stores are being loaded from SQL into memory. */
    LOADING_STORAGE,
    /** All stores hydrated and services wired; entry points are open. */
    READY,
    /**
     * A queued write failed and the runtime is retrying storage without reloading memory.
     * Entry points must reject player actions until recovery returns to READY.
     */
    RECOVERING_STORAGE,
    /** Unrecoverable error during enable or storage recovery; entry points must reject player actions. */
    FAILED,
    /** onDisable in progress; entry points must reject new actions. */
    DISABLING
}
