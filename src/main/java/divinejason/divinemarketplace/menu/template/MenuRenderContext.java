package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : bundle of static rendering infrastructure passed to every MenuSubTemplate
 */

import divinejason.divinemarketplace.menu.MenuItemFactory;
import divinejason.divinemarketplace.menu.MenuStaticButtonCache;
import divinejason.divinemarketplace.menu.MenuVisualConfig;

/**
 * Immutable bundle of rendering infrastructure shared by all sub-template applications.
 *
 * Created once per {@code MenuRenderer} instance (on startup and after a menu-config reload)
 * and passed to every {@link MenuSubTemplate#apply} call. Templates must not hold a reference
 * to this record beyond the duration of one apply call.
 */
public record MenuRenderContext(
        MenuStaticButtonCache staticCache,
        MenuItemFactory itemFactory,
        MenuVisualConfig visuals
) {}
