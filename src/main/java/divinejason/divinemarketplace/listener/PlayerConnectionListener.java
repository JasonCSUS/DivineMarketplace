package divinejason.divinemarketplace.listener;

import divinejason.divinemarketplace.menu.MenuSessionManager;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Handles player-scoped cleanup for menu sessions and pending chat prompts.
 */
public final class PlayerConnectionListener implements Listener {
    private final MenuSessionManager menuSessionManager;
    private final MarketChatPromptService chatPromptService;

    public PlayerConnectionListener(MenuSessionManager menuSessionManager, MarketChatPromptService chatPromptService) {
        this.menuSessionManager = Objects.requireNonNull(menuSessionManager, "menuSessionManager");
        this.chatPromptService = Objects.requireNonNull(chatPromptService, "chatPromptService");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        menuSessionManager.clear(event.getPlayer().getUniqueId());
        chatPromptService.clearPlayer(event.getPlayer().getUniqueId());
    }
}
