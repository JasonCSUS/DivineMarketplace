package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : previous-page and next-page navigation buttons in the inventory footer row
 */

import divinejason.divinemarketplace.menu.MenuAction;
import divinejason.divinemarketplace.menu.MenuActionType;
import divinejason.divinemarketplace.menu.MenuSlots;
import java.util.Map;
import org.bukkit.inventory.Inventory;

/**
 * Places previous-page and next-page arrow buttons in the footer row.
 *
 * <p>Each button is only placed when the corresponding flag is {@code true}, so
 * the first and last pages each show only one arrow (or none for single-page results).</p>
 *
 * <p>Constructed per render with the page boundary flags from the current {@link
 * divinejason.divinemarketplace.menu.model.PageModel}.</p>
 */
public final class PaginationFooterTemplate implements MenuSubTemplate {

    private final boolean hasPrevious;
    private final boolean hasNext;

    public PaginationFooterTemplate(boolean hasPrevious, boolean hasNext) {
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
    }

    @Override
    public void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx) {
        if (hasPrevious) {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("nav.previous", MenuSlots.PREVIOUS_PAGE),
                    ctx.staticCache().previousPage(),
                    MenuAction.simple(MenuActionType.PREVIOUS_PAGE));
        }
        if (hasNext) {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("nav.next", MenuSlots.NEXT_PAGE),
                    ctx.staticCache().nextPage(),
                    MenuAction.simple(MenuActionType.NEXT_PAGE));
        }
    }
}
