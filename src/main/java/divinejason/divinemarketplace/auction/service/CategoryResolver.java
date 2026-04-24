package divinejason.divinemarketplace.auction.service;

import org.bukkit.inventory.ItemStack;

/**
 * Chooses the top-level browse category for resolved/default items.
 */
public interface CategoryResolver {
    String resolveCategoryId(ItemStack itemStack);
}
