package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import org.bukkit.inventory.ItemStack;

public interface CustomItemTypeExtractor {
    CustomItemTypeExtractionResult inspect(ItemStack itemStack);

    default String extractItemType(ItemStack itemStack) {
        CustomItemTypeExtractionResult result = inspect(itemStack);
        return result == null ? null : result.itemType();
    }
}
