package divinejason.divinemarketplace.concurrency;

/*
 * Layer : concurrency guard
 * Owns  : short-lived player/object action locks for marketplace mutations
 * Calls : plain Java collections only
 * Avoids: Bukkit API, SQL, business-rule decisions, and menu rendering
 *
 * This gate prevents duplicate outcomes while the plugin is being moved toward
 * async services.  Menu/input events may still arrive on the Bukkit thread, but
 * a buy/cancel/claim operation should acquire both the player lock and the
 * logical object lock before it mutates runtime memory or queues persistence.
 */

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-blocking action gate for player-driven market mutations.
 *
 * <p>Use this for operations where duplicate clicks could produce duplicate
 * purchases, duplicate listing cancels, duplicate item claims, or duplicate
 * money payouts.  The gate is deliberately not a scheduler and does not run any
 * async work itself; it only answers whether an action may start right now.</p>
 */
public final class MarketActionGate {
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> activeResources = ConcurrentHashMap.newKeySet();

    /**
     * Attempts to acquire the player lock plus optional logical resource locks.
     *
     * <p>Resource keys should be stable strings such as {@code listing:<uuid>},
     * {@code item-claim:<uuid>}, or {@code money-claim:<playerUuid>}.</p>
     */
    public Optional<Permit> tryAcquire(UUID playerUuid, String... resourceKeys) {
        Objects.requireNonNull(playerUuid, "playerUuid");

        List<String> resources = normalizeResourceKeys(resourceKeys);
        if (!activePlayers.add(playerUuid)) {
            return Optional.empty();
        }

        List<String> acquiredResources = new ArrayList<>(resources.size());
        for (String resource : resources) {
            if (!activeResources.add(resource)) {
                release(playerUuid, acquiredResources);
                return Optional.empty();
            }
            acquiredResources.add(resource);
        }

        return Optional.of(new Permit(playerUuid, acquiredResources));
    }

    public boolean isPlayerLocked(UUID playerUuid) {
        return playerUuid != null && activePlayers.contains(playerUuid);
    }

    public boolean isResourceLocked(String resourceKey) {
        return resourceKey != null && activeResources.contains(resourceKey);
    }

    /** Clears all gates during plugin disable/reload teardown. */
    public void clear() {
        activePlayers.clear();
        activeResources.clear();
    }

    private List<String> normalizeResourceKeys(String... resourceKeys) {
        if (resourceKeys == null || resourceKeys.length == 0) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String resourceKey : resourceKeys) {
            if (resourceKey == null) continue;
            String trimmed = resourceKey.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return List.copyOf(unique);
    }

    private void release(UUID playerUuid, List<String> resources) {
        for (String resource : resources) {
            activeResources.remove(resource);
        }
        activePlayers.remove(playerUuid);
    }

    /** Returned by successful acquisitions. Close it after the action is fully complete. */
    public final class Permit implements AutoCloseable {
        private final UUID playerUuid;
        private final List<String> resources;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(UUID playerUuid, List<String> resources) {
            this.playerUuid = playerUuid;
            this.resources = List.copyOf(resources);
        }

        public UUID playerUuid() {
            return playerUuid;
        }

        public List<String> resources() {
            return resources;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(playerUuid, resources);
            }
        }
    }
}
