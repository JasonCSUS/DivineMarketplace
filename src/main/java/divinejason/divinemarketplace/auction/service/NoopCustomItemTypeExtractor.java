package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CustomItemTypeExtractionResult;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Deprecated(forRemoval = false)
public final class NoopCustomItemTypeExtractor implements CustomItemTypeExtractor {
    @Override
    public CustomItemTypeExtractionResult inspect(ItemStack itemStack) {
        return new CustomItemTypeExtractionResult(false, false, false, null, itemStack == null ? null : itemStack.getType(), null, null, null, null, List.of("noop extractor"));
    }
}
