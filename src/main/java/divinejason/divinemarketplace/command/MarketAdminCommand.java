package divinejason.divinemarketplace.command;


/*
 * File role: Handles the market admin command subcommand group and keeps its permission checks, parsing, and output in one file.
 */
import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.service.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.service.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.PriceRecommendationService;
import divinejason.divinemarketplace.auction.service.StoredEnchantExtractor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Public admin-command facade.
 *
 * The admin implementation is intentionally split into focused package-private
 * handlers so custom item, enchant, price, history, and review tools can evolve
 * without re-growing one large command class.
 */
public final class MarketAdminCommand {
    private final MarketAdminCommandContext context;
    private final List<MarketAdminCommandHandler> handlers;

    public MarketAdminCommand(
            DivineMarketplace plugin,
            SQLiteListingStore listingStore,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            CustomItemRegistry customItemRegistry,
            MarketRecalculationService marketRecalculationService,
            SQLiteCustomEnchantStore customEnchantStore,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService metadataLogService,
            SQLiteCustomItemOverrideStore overrideStore,
            CustomItemCollisionLogService collisionLogService,
            StoredEnchantExtractor storedEnchantExtractor
    ) {
        this.context = new MarketAdminCommandContext(
                plugin,
                listingStore,
                adminHistoryService,
                adminHistoryExportService,
                marketIndexService,
                priceRecommendationService,
                customItemRegistry,
                marketRecalculationService,
                customEnchantStore,
                customItemTypeExtractor,
                metadataLogService,
                overrideStore,
                collisionLogService,
                storedEnchantExtractor
        );
        this.handlers = List.of(
                new MarketPriceAdminCommand(context),
                new MarketHistoryAdminCommand(context),
                new MarketItemAdminCommand(context),
                new MarketEnchantAdminCommand(context),
                new MarketInspectAdminCommand(context),
                new MarketCustomOverrideAdminCommand(context),
                new MarketReviewAdminCommand(context),
                new MarketStorageAdminCommand(context)
        );
    }

    public boolean handlesRootToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return handlers.stream().anyMatch(handler -> handler.handlesRoot(normalized));
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendRichMessage("<red>Missing admin subcommand.</red>");
            return;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        for (MarketAdminCommandHandler handler : handlers) {
            if (handler.handlesRoot(root)) {
                handler.execute(sender, args);
                return;
            }
        }
        sender.sendRichMessage("<red>Unknown admin subcommand.</red>");
    }

    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>();
            for (MarketAdminCommandHandler handler : handlers) {
                suggestions.addAll(handler.rootSuggestions(sender));
            }
            return context.filterByPrefix(suggestions, context.currentToken(args));
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        for (MarketAdminCommandHandler handler : handlers) {
            if (handler.handlesRoot(root)) {
                return handler.suggest(sender, args);
            }
        }
        return List.of();
    }
}
