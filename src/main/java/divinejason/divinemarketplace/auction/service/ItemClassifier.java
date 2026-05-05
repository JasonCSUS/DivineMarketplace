package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for item classifier so command, GUI, and runtime code share one behavior boundary.
 */
import org.bukkit.inventory.ItemStack;

import divinejason.divinemarketplace.auction.model.ItemClass;

/**
 * Coarse routing helper only.
 *
 * The classifier should stay intentionally lightweight.
 * It is NOT responsible for choosing the final category or final market key.
 *
 * Pseudocode:
 *
 * if hasRecognizedCustomIdentityField(item):
 *     return CUSTOM_DATA_ITEM
 *
 * if is filled shulker:
 *     return PACKAGE
 *
 * if is enchanted book:
 *     return ENCHANTED_BOOK
 *
 * if is potion-like:
 *     return POTION_LIKE
 *
 * return DEFAULT
 *
 * Notes:
 * - weapons/armor/tools are part of DEFAULT and are handled by the
 *   default resolver path
 * - unknown custom items should be captured for admin review instead of falling
 *   back to vanilla material grouping
 */
public interface ItemClassifier {
    ItemClass classify(ItemStack itemStack);
}
