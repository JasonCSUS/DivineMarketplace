package divinejason.divinemarketplace.menu;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores MenuSession state by player UUID.
 */
public final class MenuSessionManager {
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, MenuAction>> slotActionsByPlayer = new ConcurrentHashMap<>();

    public MenuSession getOrCreate(UUID playerUuid) {
        return sessions.computeIfAbsent(playerUuid, MenuSession::create);
    }

    public void save(MenuSession session) {
        sessions.put(session.playerUuid(), session);
    }

    public void saveActions(UUID playerUuid, Map<Integer, MenuAction> actionsBySlot) {
        slotActionsByPlayer.put(playerUuid, Map.copyOf(actionsBySlot));
    }

    public Optional<MenuAction> getAction(UUID playerUuid, int rawSlot) {
        return Optional.ofNullable(slotActionsByPlayer.get(playerUuid))
                .map(actions -> actions.get(rawSlot));
    }

    public void clearActions(UUID playerUuid) {
        slotActionsByPlayer.remove(playerUuid);
    }

    public void clearAllActions() {
        slotActionsByPlayer.clear();
    }

    public void clearAll() {
        sessions.clear();
        slotActionsByPlayer.clear();
    }

    public void clear(UUID playerUuid) {
        sessions.remove(playerUuid);
        clearActions(playerUuid);
    }
}
