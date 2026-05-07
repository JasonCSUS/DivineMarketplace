package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : reserved slot for future admin-only overlay buttons on any screen
 */

import java.util.Map;
import divinejason.divinemarketplace.menu.MenuAction;
import org.bukkit.inventory.Inventory;

/**
 * Reserved template for admin-only overlay controls.
 *
 * <p>Currently a no-op. Future screens can swap {@link #NONE} for a configured
 * instance that adds cancel-any-listing, inspect-player, or audit-history buttons
 * when the viewer holds an admin permission. Adding a concrete implementation here
 * will not require changing any {@code MenuRenderer} render method — only the
 * template selection logic at the top of each method.</p>
 */
public final class AdminControlsTemplate implements MenuSubTemplate {

    /** No-op singleton used until admin overlay buttons are implemented. */
    public static final AdminControlsTemplate NONE = new AdminControlsTemplate();

    private AdminControlsTemplate() {}

    @Override
    public void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx) {
        // no admin controls registered yet
    }
}
