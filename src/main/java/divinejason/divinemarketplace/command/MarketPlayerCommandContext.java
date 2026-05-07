package divinejason.divinemarketplace.command;


/*
 * File role: Carries the dependencies needed by market player command handlers so command code does not pull directly from the plugin singleton.
 */
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.category.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.claim.ClaimService;
import divinejason.divinemarketplace.auction.service.history.HistoryService;
import divinejason.divinemarketplace.auction.service.identity.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.listing.ListingService;
import divinejason.divinemarketplace.auction.service.pricing.PriceRecommendationService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.config.ConfigService;
import divinejason.divinemarketplace.menu.MenuController;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class MarketPlayerCommandContext {
    static final DecimalFormat MONEY_FORMAT = new DecimalFormat("0.00");
    static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    final ListingService listingService;
    final ClaimService claimService;
    final SQLiteListingStore listingStore;
    final HistoryService historyService;
    final CategoryService categoryService;
    final FlattenedMarketIndexService marketIndexService;
    final PriceRecommendationService priceRecommendationService;
    final ItemIdentityResolver itemIdentityResolver;
    final MarketChatPromptService chatPromptService;
    final MenuController menuController;

    MarketPlayerCommandContext(
            ListingService listingService,
            ClaimService claimService,
            SQLiteListingStore listingStore,
            HistoryService historyService,
            CategoryService categoryService,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            ItemIdentityResolver itemIdentityResolver,
            MarketChatPromptService chatPromptService,
            MenuController menuController
    ) {
        this.listingService = listingService;
        this.claimService = claimService;
        this.listingStore = listingStore;
        this.historyService = historyService;
        this.categoryService = categoryService;
        this.marketIndexService = marketIndexService;
        this.priceRecommendationService = priceRecommendationService;
        this.itemIdentityResolver = itemIdentityResolver;
        this.chatPromptService = chatPromptService;
        this.menuController = menuController;
    }

    Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalStateException("This command can only be used by a player.");
        }
        return player;
    }

    boolean isKnownCategory(String token) {
        return loadCategoryIds(false).stream().anyMatch(category -> category.equalsIgnoreCase(token));
    }

    List<String> loadCategoryIds(boolean includeUnsorted) {
        return marketIndexService.getCategoryIds(includeUnsorted);
    }

    String categoryDisplayName(String categoryId) {
        return marketIndexService.getCategoryDisplayName(categoryId);
    }

    Collection<String> filterByPrefix(Collection<String> input, String currentToken) {
        String normalized = currentToken.toLowerCase(Locale.ROOT);
        return input.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    String currentToken(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }

    String joinArgs(String[] args, int startInclusive) {
        return joinArgs(args, startInclusive, args.length);
    }

    String joinArgs(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    long parseMoneyToHundredths(String raw) {
        return new BigDecimal(raw)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }

    SortMode defaultSortMode() {
        return ConfigService.get().defaultSortMode();
    }

    int pageSize() {
        return ConfigService.get().searchMaxResultsPerPage();
    }

    String escapeMini(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("<", "\\<").replace(">", "\\>");
    }
}
