package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for custom item type extractor so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import org.bukkit.inventory.ItemStack;

public interface CustomItemTypeExtractor {
    CustomItemTypeExtractionResult inspect(ItemStack itemStack);

    default String extractItemType(ItemStack itemStack) {
        CustomItemTypeExtractionResult result = inspect(itemStack);
        return result == null ? null : result.itemType();
    }
}
