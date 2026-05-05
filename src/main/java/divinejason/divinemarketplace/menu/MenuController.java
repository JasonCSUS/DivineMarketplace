package divinejason.divinemarketplace.menu;

import org.bukkit.entity.Player;

/**
 * Central open/refresh helper.
 */
public final class MenuController {
    private final MenuSessionManager sessionManager;
    private MenuRenderer renderer;

    public MenuController(MenuSessionManager sessionManager, MenuRenderer renderer) {
        this.sessionManager = sessionManager;
        this.renderer = renderer;
    }

    public void updateRenderer(MenuRenderer renderer) {
        this.renderer = renderer;
        sessionManager.clearAllActions();
    }

    public void open(Player player, MenuSession session) {
        sessionManager.save(session);
        MenuRenderResult renderResult = renderer.render(player, session);
        sessionManager.saveActions(player.getUniqueId(), renderResult.actionsBySlot());
        player.openInventory(renderResult.inventory());
    }

    public void refresh(Player player) {
        MenuSession session = sessionManager.getOrCreate(player.getUniqueId()).withActionLocked(false);
        open(player, session);
    }
}
