package divinejason.divinemarketplace.menu;


/*
 * File role: Pairs a freshly rendered Inventory with the slot actions that should be active for that exact view.
 */
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;

/**
 * Inventory plus the exact slot-action map generated while rendering it.
 */
public record MenuRenderResult(
        Inventory inventory,
        Map<Integer, MenuAction> actionsBySlot
) {
    public MenuRenderResult {
        actionsBySlot = Collections.unmodifiableMap(new LinkedHashMap<>(actionsBySlot));
    }

    public static MenuRenderResult empty(Inventory inventory) {
        return new MenuRenderResult(inventory, Map.of());
    }
}
