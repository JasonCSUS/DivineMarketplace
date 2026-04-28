package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Deprecated compatibility wrapper.
 * Kept only so stale references still compile until the file is removed.
 */
@Deprecated(forRemoval = false)
public final class GenericCustomItemTypeExtractor implements CustomItemTypeExtractor {
    @Override
    public CustomItemTypeExtractionResult inspect(ItemStack itemStack) {
        return new CustomItemTypeExtractionResult(false, false, false, null, itemStack == null ? null : itemStack.getType(), null, null, null, null, List.of("deprecated extractor wrapper; no match"));
    }
}
