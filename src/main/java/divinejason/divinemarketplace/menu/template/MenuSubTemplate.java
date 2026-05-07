package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : base contract for all reusable GUI chrome chunks
 */

import divinejason.divinemarketplace.menu.MenuAction;
import divinejason.divinemarketplace.menu.MenuActionType;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A reusable GUI chrome chunk that writes slots and action bindings into an
 * already-created Bukkit inventory.
 *
 * <p>Implementations are either stateless pre-built singletons (e.g.
 * {@code BorderTemplate.WITH_BACK}) or lightweight per-render value objects (e.g.
 * {@code new PaginationFooterTemplate(hasPrev, hasNext)}).  Both are safe to use
 * concurrently — {@code apply} must not mutate any instance state.</p>
 *
 * <p>All slot writes go through the shared {@link #put} helper which guards against
 * out-of-range slot numbers from misconfigured menu.yml values.</p>
 */
public interface MenuSubTemplate {

    void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx);

    /** Bounds-checked slot placement shared by all template implementations. */
    static void put(Inventory inventory, Map<Integer, MenuAction> actions,
                    int slot, ItemStack item, MenuAction action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null && action.type() != MenuActionType.NONE) {
            actions.put(slot, action);
        }
    }
}
