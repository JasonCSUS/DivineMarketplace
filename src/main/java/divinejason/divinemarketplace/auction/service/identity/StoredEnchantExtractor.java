package divinejason.divinemarketplace.auction.service.identity;


/*
 * File role: Extracts stored enchant signals from ItemStacks for identity and custom-item resolution.
 */
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Isolates stored-enchant reads for Paper 1.21.11 / Paper 26.1.2.
 *
 * Primary path:
 * - Bukkit's EnchantmentStorageMeta#getStoredEnchants()
 *
 * Fallback path:
 * - Paper data component bucket DataComponentTypes.STORED_ENCHANTMENTS
 *
 * Keeping this in one helper avoids scattering experimental component API calls
 * through command and resolver code.
 */
public final class StoredEnchantExtractor {

    public Map<Enchantment, Integer> extractStoredEnchantments(ItemStack itemStack) {
        Map<Enchantment, Integer> sorted = new TreeMap<>((left, right) -> enchantKey(left).compareTo(enchantKey(right)));

        if (itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants()) {
            sorted.putAll(storageMeta.getStoredEnchants());
        }

        try {
            if (itemStack.hasData(DataComponentTypes.STORED_ENCHANTMENTS)) {
                ItemEnchantments componentEnchantments = itemStack.getData(DataComponentTypes.STORED_ENCHANTMENTS);
                if (componentEnchantments != null) {
                    sorted.putAll(componentEnchantments.enchantments());
                }
            }
        } catch (NoClassDefFoundError | NoSuchFieldError | NoSuchMethodError ignored) {
            // Paper component API is version-specific. Bukkit meta remains the safe baseline.
        }

        return new LinkedHashMap<>(sorted);
    }

    public String enchantKey(Enchantment enchantment) {
        if (enchantment == null) {
            return "unknown:unknown";
        }
        NamespacedKey key = enchantment.getKey();
        if (key == null) {
            return "unknown:unknown";
        }
        return key.getNamespace() + ":" + key.getKey();
    }
}
