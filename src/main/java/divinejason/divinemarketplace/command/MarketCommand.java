package divinejason.divinemarketplace.command;


/*
 * File role: Dispatches /market subcommands, opens GUI entry points, and delegates admin/player work to focused handlers.
 */
import divinejason.divinemarketplace.DivineMarketplace;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemCollisionLogService;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemMetadataLogService;
import divinejason.divinemarketplace.auction.registry.custom.CustomItemRegistry;
import divinejason.divinemarketplace.auction.service.admin.AdminHistoryExportService;
import divinejason.divinemarketplace.auction.service.admin.DefaultAdminHistoryService;
import divinejason.divinemarketplace.auction.service.category.CategoryService;
import divinejason.divinemarketplace.auction.service.category.FlattenedMarketIndexService;
import divinejason.divinemarketplace.auction.service.claim.ClaimService;
import divinejason.divinemarketplace.auction.service.history.HistoryService;
import divinejason.divinemarketplace.auction.service.identity.CustomItemTypeExtractor;
import divinejason.divinemarketplace.auction.service.identity.ItemIdentityResolver;
import divinejason.divinemarketplace.auction.service.identity.StoredEnchantExtractor;
import divinejason.divinemarketplace.auction.service.listing.ListingService;
import divinejason.divinemarketplace.auction.service.enchant.DefaultEnchantmentMetadataService;
import divinejason.divinemarketplace.auction.service.pricing.MarketRecalculationService;
import divinejason.divinemarketplace.auction.service.pricing.PriceRecommendationService;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteCustomItemOverrideStore;
import divinejason.divinemarketplace.auction.storage.sqlite.SQLiteListingStore;
import divinejason.divinemarketplace.menu.MenuController;
import divinejason.divinemarketplace.menu.MenuSession;
import divinejason.divinemarketplace.menu.MenuView;
import divinejason.divinemarketplace.prompt.MarketChatPromptService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MarketCommand implements BasicCommand {
    private final MarketAdminCommand marketAdminCommand;
    private final MarketPlayerCommandContext context;
    private final MenuController menuController;
    private final MarketBrowseCommandHandler browseCommand;
    private final List<MarketPlayerCommandHandler> playerHandlers;
    private final BooleanSupplier readinessCheck;

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
            DefaultEnchantmentMetadataService enchantmentMetadataService,
            CustomItemTypeExtractor customItemTypeExtractor,
            CustomItemMetadataLogService customItemMetadataLogService,
            SQLiteCustomItemOverrideStore customItemOverrideStore,
            CustomItemCollisionLogService customItemCollisionLogService,
            StoredEnchantExtractor storedEnchantExtractor,
            BooleanSupplier readinessCheck
    ) {
        this.readinessCheck = readinessCheck;
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
                enchantmentMetadataService,
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
        if (!readinessCheck.getAsBoolean()) {
            sender.sendRichMessage("<yellow>The market is still loading. Try again in a moment.</yellow>");
            return;
        }
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
