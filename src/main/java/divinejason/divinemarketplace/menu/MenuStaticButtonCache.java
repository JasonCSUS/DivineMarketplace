package divinejason.divinemarketplace.menu;

/*
 * File role: Pre-builds one ItemStack per static chrome button so renders never reallocate them.
 */

import org.bukkit.inventory.ItemStack;

/**
 * Holds one pre-built ItemStack per static GUI element.  A new instance is
 * constructed every time the MenuRenderer is built (i.e. on startup and on
 * config/menu reload), so visual config changes are picked up automatically.
 *
 * Bukkit clones ItemStacks on {@code Inventory.setItem()}, so sharing these
 * instances across concurrent renders and across players is safe.
 */
public final class MenuStaticButtonCache {

    private final ItemStack fillerPurple;
    private final ItemStack fillerBlack;
    private final ItemStack lockedRed;
    private final ItemStack back;
    private final ItemStack previousPage;
    private final ItemStack nextPage;
    private final ItemStack previousMonth;
    private final ItemStack nextMonth;

    public MenuStaticButtonCache(MenuItemFactory factory) {
        this.fillerPurple  = factory.fillerPurple();
        this.fillerBlack   = factory.fillerBlack();
        this.lockedRed     = factory.lockedRed();
        this.back          = factory.backButton();
        this.previousPage  = factory.previousPageButton();
        this.nextPage      = factory.nextPageButton();
        this.previousMonth = factory.previousMonthButton();
        this.nextMonth     = factory.nextMonthButton();
    }

    public ItemStack fillerPurple()  { return fillerPurple; }
    public ItemStack fillerBlack()   { return fillerBlack; }
    public ItemStack lockedRed()     { return lockedRed; }
    public ItemStack back()          { return back; }
    public ItemStack previousPage()  { return previousPage; }
    public ItemStack nextPage()      { return nextPage; }
    public ItemStack previousMonth() { return previousMonth; }
    public ItemStack nextMonth()     { return nextMonth; }
}
