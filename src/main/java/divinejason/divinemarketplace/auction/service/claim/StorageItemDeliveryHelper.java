package divinejason.divinemarketplace.auction.service.claim;

/*
 * Layer : service
 * Owns  : storage item delivery helper behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Contains helper logic for storage item delivery that is shared by services with similar inventory/storage rules.
 */
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Shared storage-contents-only insertion/capacity helper.
 *
 * Intentional v1 behavior:
 * - only storage contents are considered
 * - no armor/offhand/specialized slot handling
 */
public final class StorageItemDeliveryHelper {

    public int maxInsertableAmount(PlayerInventory inventory, ItemStack baseSnapshot, int maxRequestedAmount) {
        int stackCapacity = maxPerSlot(baseSnapshot);
        int capacity = 0;

        for (ItemStack slotStack : inventory.getStorageContents()) {
            if (capacity >= maxRequestedAmount) {
                return maxRequestedAmount;
            }

            if (slotStack == null || slotStack.getType().isAir()) {
                capacity += stackCapacity;
            } else if (isSimilarIgnoringAmount(slotStack, baseSnapshot)) {
                capacity += Math.max(0, stackCapacity - slotStack.getAmount());
            }
        }

        return Math.min(capacity, maxRequestedAmount);
    }

    public boolean canFitAmount(PlayerInventory inventory, ItemStack baseSnapshot, int amount) {
        return maxInsertableAmount(inventory, baseSnapshot, amount) >= amount;
    }

    public void insertExactQuantity(PlayerInventory inventory, ItemStack baseSnapshot, int quantity) {
        int remaining = quantity;
        int stackCapacity = maxPerSlot(baseSnapshot);
        ItemStack[] storageContents = inventory.getStorageContents();

        for (int i = 0; i < storageContents.length && remaining > 0; i++) {
            ItemStack slotStack = storageContents[i];
            if (slotStack == null || slotStack.getType().isAir()) {
                continue;
            }
            if (!isSimilarIgnoringAmount(slotStack, baseSnapshot)) {
                continue;
            }

            int space = Math.max(0, stackCapacity - slotStack.getAmount());
            if (space <= 0) {
                continue;
            }

            int toInsert = Math.min(space, remaining);
            storageContents[i] = cloneWithAmount(slotStack, slotStack.getAmount() + toInsert);
            remaining -= toInsert;
        }

        for (int i = 0; i < storageContents.length && remaining > 0; i++) {
            ItemStack slotStack = storageContents[i];
            if (slotStack != null && !slotStack.getType().isAir()) {
                continue;
            }

            int toInsert = Math.min(stackCapacity, remaining);
            storageContents[i] = cloneWithAmount(baseSnapshot, toInsert);
            remaining -= toInsert;
        }

        if (remaining > 0) {
            throw new IllegalStateException("Inventory insertion failed unexpectedly after capacity precheck.");
        }

        inventory.setStorageContents(storageContents);
    }

    private int maxPerSlot(ItemStack itemStack) {
        return Math.max(1, itemStack.getMaxStackSize());
    }

    private boolean isSimilarIgnoringAmount(ItemStack left, ItemStack right) {
        ItemStack leftCopy = normalizeSnapshotAmount(left);
        ItemStack rightCopy = normalizeSnapshotAmount(right);
        return leftCopy.isSimilar(rightCopy);
    }

    private ItemStack normalizeSnapshotAmount(ItemStack input) {
        ItemStack copy = input.clone();
        copy.setAmount(1);
        return copy;
    }

    private ItemStack cloneWithAmount(ItemStack input, int amount) {
        ItemStack copy = input.clone();
        copy.setAmount(amount);
        return copy;
    }
}
