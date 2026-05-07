package divinejason.divinemarketplace.auction.service.category;

/*
 * Layer : service
 * Owns  : category behavior
 * Calls : stores (auction/storage) and registries only — never GUI or commands
 */


/*
 * File role: Implements category service behavior using the SQLite stores, config registries, and item identity services.
 */
import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.auction.model.EnchantBrowseSummary;
import divinejason.divinemarketplace.auction.model.EnchantmentDefinition;
import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import divinejason.divinemarketplace.auction.service.enchant.EnchantmentMetadataService;
import divinejason.divinemarketplace.config.ConfigService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SQLite/runtime-index-backed category browse service.
 *
 * Count semantics:
 * - categoryCounts track active listing records for the top-level category badge
 * - group listedQuantity tracks total listed item quantity inside a market group
 */
public final class DefaultCategoryService implements CategoryService {
    private final ActiveListingLookup listingLookup;
    private final FlattenedMarketIndexService marketIndexService;
    private final EnchantmentMetadataService enchantmentMetadataService;

    private final List<String> categoryOrder = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    private final Map<String, GroupAggregate> groupsByMarketKey = new LinkedHashMap<>();

    public DefaultCategoryService(JavaPlugin plugin, ActiveListingLookup listingLookup, FlattenedMarketIndexService marketIndexService) {
        this(listingLookup, marketIndexService, null);
    }

    public DefaultCategoryService(ActiveListingLookup listingLookup, FlattenedMarketIndexService marketIndexService) {
        this(listingLookup, marketIndexService, null);
    }

    public DefaultCategoryService(ActiveListingLookup listingLookup, FlattenedMarketIndexService marketIndexService, EnchantmentMetadataService enchantmentMetadataService) {
        this.listingLookup = Objects.requireNonNull(listingLookup, "listingLookup");
        this.marketIndexService = Objects.requireNonNull(marketIndexService, "marketIndexService");
        this.enchantmentMetadataService = enchantmentMetadataService;
        reload();
    }

    public synchronized void reload() {
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
            out.add(new CategorySummary(categoryId, marketIndexService.getCategoryDisplayName(categoryId), marketIndexService.getCategoryIconKey(categoryId), count));
        }
        return pageCategories(out, page, pageSize);
    }

    @Override
    public synchronized String getCategoryDisplayName(String categoryId) {
        return marketIndexService.getCategoryDisplayName(categoryId);
    }

    @Override
    public synchronized List<SubcategorySummary> getMarketGroupsForCategory(String categoryId, int page, int pageSize) {
        List<SubcategorySummary> out = groupsByMarketKey.values().stream()
                .filter(group -> group.categoryId.equalsIgnoreCase(categoryId))
                .filter(group -> !"unsorted".equalsIgnoreCase(group.categoryId))
                .sorted(Comparator.comparingInt((GroupAggregate group) -> group.listedQuantity).reversed()
                        .thenComparing(group -> group.marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSubcategorySummary)
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
                .sorted(Comparator.comparingInt((GroupAggregate group) -> group.listedQuantity).reversed()
                        .thenComparing(group -> group.marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSubcategorySummary)
                .toList();
        return pageGroups(out, page, pageSize);
    }

    @Override
    public synchronized List<EnchantBrowseSummary> getEnchantedBookTargetGroups(int page, int pageSize) {
        Map<EnchantBrowseGroup, EnchantGroupAggregate> aggregates = new EnumMap<>(EnchantBrowseGroup.class);
        for (GroupAggregate group : groupsByMarketKey.values()) {
            if (!isEnchantedBookGroup(group)) {
                continue;
            }
            for (EnchantBrowseGroup browseGroup : browseGroupsForMarketKey(group.marketKey)) {
                if (browseGroup == EnchantBrowseGroup.UNKNOWN) {
                    continue;
                }
                EnchantGroupAggregate aggregate = aggregates.computeIfAbsent(browseGroup, EnchantGroupAggregate::new);
                aggregate.activeMarketGroupCount++;
                aggregate.listedQuantity += group.listedQuantity;
            }
        }

        List<EnchantBrowseSummary> out = aggregates.values().stream()
                .sorted(Comparator.comparingInt((EnchantGroupAggregate aggregate) -> aggregate.listedQuantity).reversed()
                        .thenComparing(aggregate -> aggregate.group.displayName(), String.CASE_INSENSITIVE_ORDER))
                .map(aggregate -> new EnchantBrowseSummary(
                        aggregate.group,
                        aggregate.group.displayName(),
                        aggregate.activeMarketGroupCount,
                        aggregate.listedQuantity
                ))
                .toList();
        return pageEnchantGroups(out, page, pageSize);
    }

    @Override
    public synchronized List<SubcategorySummary> getEnchantedBookMarketGroups(EnchantBrowseGroup browseGroup, int page, int pageSize) {
        if (browseGroup == null || browseGroup == EnchantBrowseGroup.UNKNOWN) {
            return List.of();
        }

        List<SubcategorySummary> out = groupsByMarketKey.values().stream()
                .filter(this::isEnchantedBookGroup)
                .filter(group -> browseGroupsForMarketKey(group.marketKey).contains(browseGroup))
                .sorted(Comparator.comparingInt((GroupAggregate group) -> group.listedQuantity).reversed()
                        .thenComparing(group -> group.marketDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSubcategorySummary)
                .toList();
        return pageGroups(out, page, pageSize);
    }

    @Override
    public synchronized void refreshIndexesFor(String marketKey, String categoryId) {
        rebuildCategoryIndex(normalizeCategoryId(categoryId));
    }

    @Override
    public synchronized void refreshIndexesForCategories(Collection<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        categoryIds.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeCategoryId)
                .distinct()
                .forEach(this::rebuildCategoryIndex);
    }

    @Override
    public synchronized void rebuildAllIndexes() {
        categoryCounts.clear();
        groupsByMarketKey.clear();

        for (String categoryId : categoryOrder) {
            categoryCounts.put(categoryId, 0);
        }

        for (Listing listing : listingLookup.getAllActiveListings()) {
            addListingToIndexes(listing);
        }
    }

    private void rebuildCategoryIndex(String categoryId) {
        ensureCategoryKnown(categoryId);
        categoryCounts.put(categoryId, 0);
        groupsByMarketKey.entrySet().removeIf(entry -> entry.getValue().categoryId.equalsIgnoreCase(categoryId));

        for (Listing listing : listingLookup.getAllActiveListings()) {
            ListingCategorySnapshot snapshot = snapshotFor(listing);
            if (snapshot.categoryId().equalsIgnoreCase(categoryId)) {
                addListingToIndexes(listing, snapshot);
            }
        }
    }

    private void addListingToIndexes(Listing listing) {
        addListingToIndexes(listing, snapshotFor(listing));
    }

    private void addListingToIndexes(Listing listing, ListingCategorySnapshot snapshot) {
        ensureCategoryKnown(snapshot.categoryId());
        categoryCounts.merge(snapshot.categoryId(), 1, Integer::sum);

        GroupAggregate group = groupsByMarketKey.computeIfAbsent(
                listing.marketKey(),
                ignored -> new GroupAggregate(
                        snapshot.categoryId(),
                        listing.marketKey(),
                        snapshot.displayName(),
                        listing.listedItemSnapshot().getType().name(),
                        0
                )
        );
        group.listedQuantity += listing.amount();
    }

    private ListingCategorySnapshot snapshotFor(Listing listing) {
        FlattenedMarketIndexEntry entry = marketIndexService.getByMarketKey(listing.marketKey());
        String categoryId = entry != null ? entry.categoryId() : listing.categoryId();
        String displayName = entry != null ? entry.marketDisplayName() : listing.marketDisplayName();
        return new ListingCategorySnapshot(normalizeCategoryId(categoryId), displayName);
    }

    private boolean isEnchantedBookGroup(GroupAggregate group) {
        return "enchanted_books".equalsIgnoreCase(group.categoryId)
                || group.marketKey.startsWith("enchanted_book:");
    }

    private Set<EnchantBrowseGroup> browseGroupsForMarketKey(String marketKey) {
        if (enchantmentMetadataService == null || marketKey == null || !marketKey.startsWith("enchanted_book:")) {
            return Set.of(EnchantBrowseGroup.UNKNOWN);
        }

        List<String> enchantKeys = enchantKeysFromMarketKey(marketKey);
        if (enchantKeys.isEmpty()) {
            return Set.of(EnchantBrowseGroup.UNKNOWN);
        }

        EnumSet<EnchantBrowseGroup> groups = EnumSet.noneOf(EnchantBrowseGroup.class);
        for (String enchantKey : enchantKeys) {
            EnchantmentDefinition definition = enchantmentMetadataService.resolveDefinition(enchantKey);
            if (definition == null || definition.browseGroups().isEmpty()) {
                groups.add(EnchantBrowseGroup.UNKNOWN);
                continue;
            }
            groups.addAll(definition.browseGroups());
        }
        return groups.isEmpty() ? Set.of(EnchantBrowseGroup.UNKNOWN) : Set.copyOf(groups);
    }

    private List<String> enchantKeysFromMarketKey(String marketKey) {
        if (marketKey == null || !marketKey.startsWith("enchanted_book:")) {
            return List.of();
        }

        if (marketKey.startsWith("enchanted_book:mixed|")) {
            List<String> keys = new ArrayList<>();
            String[] parts = marketKey.substring("enchanted_book:mixed|".length()).split("\\|");
            for (String part : parts) {
                String key = enchantKeyFromKeyLevelToken(part);
                if (key != null) {
                    keys.add(key);
                }
            }
            return keys;
        }

        String single = marketKey.substring("enchanted_book:".length());
        String key = enchantKeyFromKeyLevelToken(single);
        return key == null ? List.of() : List.of(key);
    }

    private String enchantKeyFromKeyLevelToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int lastColon = token.lastIndexOf(':');
        if (lastColon <= 0 || lastColon >= token.length() - 1) {
            return null;
        }
        String levelToken = token.substring(lastColon + 1);
        for (int i = 0; i < levelToken.length(); i++) {
            if (!Character.isDigit(levelToken.charAt(i))) {
                return null;
            }
        }
        return token.substring(0, lastColon);
    }

    private SubcategorySummary toSubcategorySummary(GroupAggregate group) {
        return new SubcategorySummary(
                group.marketKey,
                group.marketDisplayName,
                group.previewIconKey,
                group.listedQuantity
        );
    }

    private void ensureCategoryKnown(String categoryId) {
        if (!categoryCounts.containsKey(categoryId)) {
            categoryCounts.put(categoryId, 0);
        }
        if (categoryOrder.stream().noneMatch(existing -> existing.equalsIgnoreCase(categoryId))) {
            categoryOrder.add(categoryId);
        }
    }

    private String normalizeCategoryId(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? "unsorted" : categoryId;
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

    private List<EnchantBrowseSummary> pageEnchantGroups(List<EnchantBrowseSummary> items, int page, int pageSize) {
        int start = Math.max(0, page * pageSize);
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), start + pageSize);
        return List.copyOf(items.subList(start, end));
    }

    private record ListingCategorySnapshot(String categoryId, String displayName) {
    }

    private static final class GroupAggregate {
        private final String categoryId;
        private final String marketKey;
        private final String marketDisplayName;
        private final String previewIconKey;
        private int listedQuantity;

        private GroupAggregate(String categoryId, String marketKey, String marketDisplayName, String previewIconKey, int listedQuantity) {
            this.categoryId = categoryId;
            this.marketKey = marketKey;
            this.marketDisplayName = marketDisplayName;
            this.previewIconKey = previewIconKey;
            this.listedQuantity = listedQuantity;
        }
    }

    private static final class EnchantGroupAggregate {
        private final EnchantBrowseGroup group;
        private int activeMarketGroupCount;
        private int listedQuantity;

        private EnchantGroupAggregate(EnchantBrowseGroup group) {
            this.group = group;
        }
    }

    public interface ActiveListingLookup {
        List<Listing> getAllActiveListings();
    }
}
