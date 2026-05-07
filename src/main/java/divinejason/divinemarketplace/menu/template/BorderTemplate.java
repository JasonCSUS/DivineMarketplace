package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : full-inventory filler fill and optional back-button chrome
 */

import divinejason.divinemarketplace.menu.MenuAction;
import divinejason.divinemarketplace.menu.MenuActionType;
import divinejason.divinemarketplace.menu.MenuSlots;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Fills every slot with the purple filler pane and optionally places the back button.
 *
 * <p>Use {@link #WITH_BACK} for any screen that has a parent to return to.
 * Use {@link #WITHOUT_BACK} for the root main screen only.</p>
 *
 * <p>Both instances are pre-built singletons — no allocation per render.</p>
 */
public final class BorderTemplate implements MenuSubTemplate {

    /** Pre-built instance for screens with a back-navigation button. */
    public static final BorderTemplate WITH_BACK = new BorderTemplate(true);

    /** Pre-built instance for root screens (main menu) that have no back button. */
    public static final BorderTemplate WITHOUT_BACK = new BorderTemplate(false);

    private final boolean includeBack;

    private BorderTemplate(boolean includeBack) {
        this.includeBack = includeBack;
    }

    @Override
    public void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx) {
        ItemStack filler = ctx.staticCache().fillerPurple();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        if (includeBack) {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("nav.back", MenuSlots.BACK),
                    ctx.staticCache().back(),
                    MenuAction.simple(MenuActionType.BACK));
        }
    }
}
