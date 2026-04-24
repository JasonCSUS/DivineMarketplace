package divinejason.divinemarketplace.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Central open/refresh helper.
 */
public final class MenuController {
    private final MenuSessionManager sessionManager;
    private final MenuRenderer renderer;

    public MenuController(MenuSessionManager sessionManager, MenuRenderer renderer) {
        this.sessionManager = sessionManager;
        this.renderer = renderer;
    }

    public void open(Player player, MenuSession session) {
        sessionManager.save(session);
        Inventory inventory = renderer.render(player, session);
        player.openInventory(inventory);
    }

    public void refresh(Player player) {
        MenuSession session = sessionManager.getOrCreate(player.getUniqueId());
        Inventory inventory = renderer.render(player, session);
        player.openInventory(inventory);
    }
}
