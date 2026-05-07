package divinejason.divinemarketplace.auction.service.category;


/*
 * File role: Defines the service contract for category resolver so command, GUI, and runtime code share one behavior boundary.
 */
import org.bukkit.inventory.ItemStack;

/**
 * Chooses the top-level browse category for resolved/default items.
 */
public interface CategoryResolver {
    String resolveCategoryId(ItemStack itemStack);
}
