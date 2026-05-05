package divinejason.divinemarketplace.menu;


/*
 * File role: Tags marketplace inventories so the listener can distinguish plugin menus from normal player inventories.
 */
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Lightweight holder used only to identify plugin-owned inventories safely.
 */
public final class MarketMenuHolder implements InventoryHolder {
    private final MenuView view;
    private final String contextKey;

    public MarketMenuHolder(MenuView view, String contextKey) {
        this.view = view;
        this.contextKey = contextKey;
    }

    public MenuView view() {
        return view;
    }

    public String contextKey() {
        return contextKey;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
