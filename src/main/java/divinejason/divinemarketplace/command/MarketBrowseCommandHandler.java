package divinejason.divinemarketplace.command;


/*
 * File role: Handles player-facing market browse command subcommands and translates service results into chat/GUI feedback.
 */
import divinejason.divinemarketplace.auction.model.EnchantBrowseGroup;
import divinejason.divinemarketplace.menu.MenuSession;
import divinejason.divinemarketplace.menu.MenuView;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class MarketBrowseCommandHandler implements MarketPlayerCommandHandler {
    private final MarketPlayerCommandContext context;

    MarketBrowseCommandHandler(MarketPlayerCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRootToken(String rootToken) {
        return "all".equals(rootToken)
                || "search".equals(rootToken)
                || context.isKnownCategory(rootToken);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            handleMain(sender);
            return;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "all" -> handleAll(sender);
            case "search" -> handleSearch(sender, args);
            default -> handleCategory(sender, args);
        }
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length == 2 && "search".equalsIgnoreCase(args[0])) {
            return context.filterByPrefix(context.marketIndexService.getPlayerDisplayNames(), context.currentToken(args));
        }
        if (args.length > 1 && "enchanted_books".equalsIgnoreCase(args[0])) {
            return suggestEnchantedBooks(args);
        }
        return List.of();
    }

    void handleMain(CommandSender sender) {
        Player player = context.requirePlayer(sender);
        context.menuController.open(player, MenuSession.create(player.getUniqueId()));
    }

    private void handleAll(CommandSender sender) {
        Player player = context.requirePlayer(sender);
        MenuSession root = MenuSession.create(player.getUniqueId());
        context.menuController.open(player, root.pushAndOpen(root
                .withView(MenuView.LISTING_BROWSER)
                .withSelectedMarketKey(null)
                .withPage(0)));
    }

    private void handleCategory(CommandSender sender, String[] args) {
        String categoryId = args[0].toLowerCase(Locale.ROOT);
        if ("enchanted_books".equalsIgnoreCase(categoryId)) {
            handleEnchantedBooks(sender, args);
            return;
        }

        Player player = context.requirePlayer(sender);
        MenuSession root = MenuSession.create(player.getUniqueId());
        context.menuController.open(player, root.pushAndOpen(root
                .withView(MenuView.CATEGORY_BROWSER)
                .withSelectedCategoryId(categoryId)
                .withSelectedEnchantGroup(null)
                .withSelectedMarketKey(null)
                .withPage(0)));
    }

    private void handleEnchantedBooks(CommandSender sender, String[] args) {
        Player player = context.requirePlayer(sender);
        MenuSession root = MenuSession.create(player.getUniqueId());
        MenuSession enchantTargets = root.pushAndOpen(root
                .withView(MenuView.CATEGORY_BROWSER)
                .withSelectedCategoryId("enchanted_books")
                .withSelectedEnchantGroup(null)
                .withSelectedMarketKey(null)
                .withPage(0));

        if (args.length <= 1) {
            context.menuController.open(player, enchantTargets);
            return;
        }

        EnchantBrowseGroup browseGroup = EnchantBrowseGroup.fromCommandToken(args[1]);
        if (browseGroup == EnchantBrowseGroup.UNKNOWN) {
            sender.sendRichMessage("<red>Unknown enchanted-book group:</red> <gray>" + context.escapeMini(args[1]) + "</gray>");
            return;
        }

        context.menuController.open(player, enchantTargets.pushAndOpen(enchantTargets
                .withSelectedEnchantGroup(browseGroup.commandToken())
                .withPage(0)));
    }

    private void handleSearch(CommandSender sender, String[] args) {
        Player player = context.requirePlayer(sender);
        if (args.length < 2) {
            context.chatPromptService.promptSearch(player, MenuSession.create(player.getUniqueId()));
            return;
        }

        String query = context.joinArgs(args, 1);
        MenuSession root = MenuSession.create(player.getUniqueId());
        context.menuController.open(player, root.pushAndOpen(root
                .withView(MenuView.SEARCH_RESULTS)
                .withSearchQuery(query)
                .withPage(0)));
    }

    private Collection<String> suggestEnchantedBooks(String[] args) {
        if (args.length == 2) {
            return context.filterByPrefix(List.of("universal", "tools", "sword", "axe", "armor", "helmet", "chestplate", "leggings", "boots", "pickaxe", "shovel", "hoe", "elytra", "bow", "crossbow", "shield", "shears", "trident"), context.currentToken(args));
        }
        return List.of();
    }
}
