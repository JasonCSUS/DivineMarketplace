package divinejason.divinemarketplace.menu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores MenuSession state by player UUID.
 */
public final class MenuSessionManager {
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    public MenuSession getOrCreate(UUID playerUuid) {
        return sessions.computeIfAbsent(playerUuid, MenuSession::create);
    }

    public void save(MenuSession session) {
        sessions.put(session.playerUuid(), session);
    }

    public void clear(UUID playerUuid) {
        sessions.remove(playerUuid);
    }
}
