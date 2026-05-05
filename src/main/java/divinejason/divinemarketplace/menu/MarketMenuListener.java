package divinejason.divinemarketplace.menu;


/*
 * File role: Contains GUI infrastructure for market menu listener behavior.
 */
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin Paper-facing listener layer.
 *
 * All market menus cancel normal chest behavior and route clicks into the
 * menu system instead.
 */
public final class MarketMenuListener implements Listener {
    private final JavaPlugin plugin;
    private final MenuSessionManager sessionManager;
    private final MenuClickRouter clickRouter;

    public MarketMenuListener(JavaPlugin plugin, MenuSessionManager sessionManager, MenuClickRouter clickRouter) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenuHolder)) {
                sessionManager.clear(player.getUniqueId());
            }
        });
    }
}
