package divinejason.divinemarketplace.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Thin Paper-facing listener layer.
 *
 * All market menus cancel normal chest behavior and route clicks into the
 * menu system instead.
 */
public final class MarketMenuListener implements Listener {
    private final MenuClickRouter clickRouter;

    public MarketMenuListener(MenuClickRouter clickRouter) {
        this.clickRouter = clickRouter;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return;
        }

        clickRouter.handleClick(player, holder, rawSlot, event.getClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketMenuHolder) {
            event.setCancelled(true);
        }
    }
}
