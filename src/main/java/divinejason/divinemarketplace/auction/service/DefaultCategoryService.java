package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.config.ConfigService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * SQLite/runtime-index-backed category browse service.
 *
 * Count semantics:
 * - categoryCounts track active listing records for the top-level category badge
 * - group activeListingCount tracks total listed item quantity inside a market group
 */
public final class DefaultCategoryService implements CategoryService {
    private final BinaryListingLookup listingLookup;
    private final FlattenedMarketIndexService marketIndexService;

    private final List<String> categoryOrder = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private final Map<String, GroupAggregate> groupsByMarketKey = new LinkedHashMap<>();

    public DefaultCategoryService(JavaPlugin plugin, BinaryListingLookup listingLookup, FlattenedMarketIndexService marketIndexService) {
        this(listingLookup, marketIndexService);
    }

    public DefaultCategoryService(BinaryListingLookup listingLookup, FlattenedMarketIndexService marketIndexService) {
        this.listingLookup = Objects.requireNonNull(listingLookup, "listingLookup");
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        reload();
    }

    public void reload() {
        categoryOrder.clear();
        categoryOrder.addAll(marketIndexService.getCategoryIds(true));
        rebuildAllIndexes();
    }

    @Override
    public synchronized List<CategorySummary> getTopLevelCategories(int page, int pageSize) {
        List<CategorySummary> out = new ArrayList<>();
        for (String categoryId : categoryOrder) {
            if ("unsorted".equalsIgnoreCase(categoryId)) {
                continue;
            }
            int count = categoryCounts.getOrDefault(categoryId, 0);
            if (!ConfigService.get().showEmptyTopLevelCategories() && count <= 0) {
                continue;
            }
            out.add(new CategorySummary(categoryId, count));
        }
        return pageCategories(out, page, pageSize);
    }

    @Override
    public synchronized List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page, int pageSize) {
        List<SubcategorySummary> out = groupsByMarketKey.values().stream()
                .filter(group -> group.categoryId.equalsIgnoreCase(categoryId))
                .filter(group -> !"unsorted".equalsIgnoreCase(group.categoryId))
                .sorted(Comparator.comparingInt((GroupAggregate g) -> g.activeListingCount).reversed()
                        .thenComparing(g -> g.marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new SubcategorySummary(
                        group.marketKey,
                        group.marketDisplayName,
                        group.previewIconKey,
                        group.activeListingCount
                ))
                .toList();
        return pageGroups(out, page, pageSize);
    }

    @Override
    public synchronized List<SubcategorySummary> searchMarketGroups(String query, int page, int pageSize) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<SubcategorySummary> out = groupsByMarketKey.values().stream()
                .filter(group -> !"unsorted".equalsIgnoreCase(group.categoryId))
                .filter(group -> group.marketKey.toLowerCase(Locale.ROOT).contains(normalized)
                        || group.marketDisplayName.toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparingInt((GroupAggregate g) -> g.activeListingCount).reversed()
                        .thenComparing(g -> g.marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(group -> new SubcategorySummary(
                        group.marketKey,
                        group.marketDisplayName,
                        group.previewIconKey,
                        group.activeListingCount
                ))
                .toList();
        return pageGroups(out, page, pageSize);
    }

    @Override
    public synchronized void refreshIndexesFor(String marketKey, String categoryId) {
        rebuildAllIndexes();
    }

    @Override
    public synchronized void rebuildAllIndexes() {
        categoryCounts.clear();
        groupsByMarketKey.clear();

        for (String categoryId : categoryOrder) {
            categoryCounts.put(categoryId, 0);
        }

        for (Listing listing : listingLookup.getAllActiveListings()) {
            String marketKey = listing.marketKey();
            FlattenedMarketIndexEntry entry = marketIndexService.getByMarketKey(marketKey);
            String categoryId = entry != null ? entry.categoryId() : listing.categoryId();
            String displayName = entry != null ? entry.marketDisplayName() : listing.marketDisplayName();

            categoryCounts.merge(categoryId, 1, Integer::sum);

            GroupAggregate group = groupsByMarketKey.computeIfAbsent(
                    marketKey,
                    ignored -> new GroupAggregate(
                            categoryId,
                            marketKey,
                            displayName,
                            listing.listedItemSnapshot().getType().name(),
                            0
                    )
            );
            group.activeListingCount += listing.amount();
        }
    }

    private List<SubcategorySummary> pageGroups(List<SubcategorySummary> items, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), start + pageSize);
        return List.copyOf(items.subList(start, end));
    }

    private List<CategorySummary> pageCategories(List<CategorySummary> items, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), start + pageSize);
        return List.copyOf(items.subList(start, end));
    }

    private static final class GroupAggregate {
        private final String categoryId;
        private final String marketKey;
        private final String marketDisplayName;
        private final String previewIconKey;
        private int activeListingCount;

        private GroupAggregate(String categoryId, String marketKey, String marketDisplayName, String previewIconKey, int activeListingCount) {
            this.categoryId = categoryId;
            this.marketKey = marketKey;
            this.marketDisplayName = marketDisplayName;
            this.previewIconKey = previewIconKey;
            this.activeListingCount = activeListingCount;
        }
    }

    public interface BinaryListingLookup {
        List<Listing> getAllActiveListings();
    }
}
