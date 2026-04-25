package divinejason.divinemarketplace.auction.service;

import org.bukkit.inventory.ItemStack;

/**
 * Minimal placeholder extractor used until plugin-specific custom-item APIs
 * (such as ItemsAdder hooks) are wired in.
 */
public final class NoopCustomItemTypeExtractor implements CustomItemTypeExtractor {
    @Override
    public String extractItemType(ItemStack itemStack) {
        return null;
    }
}
