package divinejason.divinemarketplace.command;


/*
 * File role: Dispatches /market subcommands, opens GUI entry points, and delegates admin/player work to focused handlers.
 */
import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomEnchantStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.persistence.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.auction.service.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.CategoryService;
import divinejason.divinemarketplace.auction.service.ClaimService;
import divinejason.divinemarketplace.auction.service.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.service.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.service.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.HistoryService;
import divinejason.divinemarketplace.auction.service.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.ListingService;
import divinejason.divinemarketplace.auction.service.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.PriceRecommendationService;
import divinejason.divinemarketplace.auction.service.StoredEnchantExtractor;
import divinejason.divinemarketplace.menu.MenuController;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import divinejason.divinemarketplace.menu.MenuSession;
import divinejason.divinemarketplace.menu.MenuView;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@NullMarked
public final class MarketCommand implements BasicCommand {
    private final MarketAdminCommand marketAdminCommand;
    private final MarketPlayerCommandContext context;
    private final MenuController menuController;
    private final MarketBrowseCommandHandler browseCommand;
    private final List<MarketPlayerCommandHandler> playerHandlers;

    public MarketCommand(
            DivineMarketplace plugin,
            MenuController menuController,
            MarketChatPromptService chatPromptService,
            ListingService listingService,
            ClaimService claimService,
            SQLiteListingStore listingStore,
            HistoryService historyService,
            DefaultAdminHistoryService adminHistoryService,
            AdminHistoryExportService adminHistoryExportService,
            CategoryService categoryService,
            FlattenedMarketIndexService marketIndexService,
            PriceRecommendationService priceRecommendationService,
            ItemIdentityResolver itemIdentityResolver,
            CustomItemRegistry customItemRegistry,
            MarketRecalculationService marketRecalculationService,
            SQLiteCustomEnchantStore customEnchantStore,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService customItemMetadataLogService,
            SQLiteCustomItemOverrideStore customItemOverrideStore,
            CustomItemCollisionLogService customItemCollisionLogService,
            StoredEnchantExtractor storedEnchantExtractor
    ) {
        this.menuController = menuController;
        this.context = new MarketPlayerCommandContext(
                listingService,
                claimService,
                listingStore,
                historyService,
                categoryService,
                marketIndexService,
                priceRecommendationService,
                itemIdentityResolver,
                chatPromptService,
                menuController
        );
        this.browseCommand = new MarketBrowseCommandHandler(context);
        this.playerHandlers = List.of(
                browseCommand,
                new MarketClaimCommandHandler(context),
                new MarketHistoryCommandHandler(context),
                new MarketListingCommandHandler(context),
                new MarketPriceCheckCommandHandler(context)
        );
        this.marketAdminCommand = new MarketAdminCommand(
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
                customItemMetadataLogService,
                customItemOverrideStore,
                customItemCollisionLogService,
                storedEnchantExtractor
        );
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        try {
            if (args.length == 0) {
                var player = context.requirePlayer(sender);
                menuController.open(player, MenuSession.create(player.getUniqueId()));
                return;
            }

            String root = args[0].toLowerCase(Locale.ROOT);
            if ("claim".equals(root) && args.length == 1) {
                var player = context.requirePlayer(sender);
                menuController.open(player, MenuSession.create(player.getUniqueId()).pushAndOpen(MenuSession.create(player.getUniqueId()).withView(MenuView.CLAIMS)));
                return;
            }

            if (marketAdminCommand.handlesRootToken(root)) {
                marketAdminCommand.execute(sender, args);
                return;
            }

            for (MarketPlayerCommandHandler handler : playerHandlers) {
                if (handler.handlesRootToken(root)) {
                    handler.execute(sender, args);
                    return;
                }
            }

            sender.sendRichMessage("<red>Unknown market command or category.</red> <gray>Try /market search &lt;query&gt;.</gray>");
        } catch (SecurityException exception) {
            sender.sendRichMessage("<red>You do not have permission to use that command.</red>");
        } catch (Exception exception) {
            sender.sendRichMessage("<red>Command failed:</red> <gray>" + context.escapeMini(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()) + "</gray>");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length <= 1) {
            Set<String> suggestions = new LinkedHashSet<>();
            suggestions.add("all");
            suggestions.add("search");
            suggestions.add("claim");
            suggestions.add("list");
            suggestions.add("pricecheck");
            suggestions.add("history");
            suggestions.add("pricehistory");
            suggestions.addAll(context.loadCategoryIds(false));
            suggestions.addAll(marketAdminCommand.suggest(sender, args.length == 0 ? new String[0] : args));
            return context.filterByPrefix(suggestions, context.currentToken(args));
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        if (marketAdminCommand.handlesRootToken(root)) {
            return marketAdminCommand.suggest(sender, args);
        }

        for (MarketPlayerCommandHandler handler : playerHandlers) {
            if (handler.handlesRootToken(root)) {
                return handler.suggest(sender, args);
            }
        }

        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("divinemarketplace.use") || sender.hasPermission("divinemarketplace.admin");
    }
}
