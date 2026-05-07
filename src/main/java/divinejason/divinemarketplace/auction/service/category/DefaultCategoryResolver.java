package divinejason.divinemarketplace.auction.service.category;


/*
 * File role: Implements category resolver behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves categories from the flattened runtime market index.
 */
public final class DefaultCategoryResolver implements CategoryResolver {
    private final FlattenedMarketIndexService marketIndexService;
    private final Map<Material, String> materialToCategoryId = new LinkedHashMap<>();

    public DefaultCategoryResolver(FlattenedMarketIndexService marketIndexService) {
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        reload();
    }

    @Override
    public String resolveCategoryId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "unsorted";
        }
        return materialToCategoryId.getOrDefault(itemStack.getType(), "unsorted");
    }

    public void reload() {
        materialToCategoryId.clear();
        for (FlattenedMarketIndexEntry entry : marketIndexService.entries()) {
            if (entry.requiredMaterial() != null && entry.isVanilla()) {
                materialToCategoryId.put(entry.requiredMaterial(), entry.categoryId());
            }
        }
    }
}
