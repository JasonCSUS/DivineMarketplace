package divinejason.divinemarketplace.command;

import divinejason.divinemarketplace.auction.model.FlattenedMarketIndexEntry;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class MarketReviewAdminCommand implements MarketAdminCommandHandler {
    private final MarketAdminCommandContext context;

    MarketReviewAdminCommand(MarketAdminCommandContext context) {
        this.context = context;
    }

    @Override
    public boolean handlesRoot(String root) {
        return root.equalsIgnoreCase("admin");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("flagged")) {
            handleAdminFlagged(sender, args);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("collisions")) {
            handleAdminCollisions(sender, args);
            return;
        }
        sender.sendRichMessage("<yellow>Usage:</yellow> <gray>/market admin flagged [page]</gray> <yellow>or</yellow> <gray>/market admin collisions [page]</gray>");
    }

    @Override
    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.flagged.view")) {
            suggestions.add("flagged");
        }
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.collisions.view")) {
            suggestions.add("collisions");
        }
        return context.filterByPrefix(suggestions, context.currentToken(args));
    }

    @Override
    public Collection<String> rootSuggestions(CommandSender sender) {
        if (context.hasAdminPermission(sender, "divinemarketplace.admin.flagged.view")
                || context.hasAdminPermission(sender, "divinemarketplace.admin.collisions.view")) {
            return List.of("admin");
        }
        return List.of();
    }

    private void handleAdminFlagged(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.flagged.view");
        int page = parsePage(sender, args, 2);
        if (page < 1) {
            return;
        }

        int pageSize = 8;
        List<FlattenedMarketIndexEntry> flagged = context.marketIndexService.getFlaggedEntries();
        int total = flagged.size();
        int start = (page - 1) * pageSize;
        int end = Math.min(total, start + pageSize);

        sender.sendRichMessage("<gold>Flagged / Unsorted Entries</gold> <gray>page " + page + "</gray>");
        if (flagged.isEmpty() || start >= total) {
            sender.sendRichMessage("<gray>No unsorted market entries found for that page.</gray>");
            return;
        }

        for (FlattenedMarketIndexEntry entry : flagged.subList(start, end)) {
            sender.sendRichMessage("<gray>-</gray> <white>" + context.escapeMini(entry.marketDisplayName()) + "</white> <dark_gray>"
                    + context.escapeMini(entry.marketKey()) + "</dark_gray> <gray>(" + context.escapeMini(entry.categoryId()) + ")</gray> <dark_gray>"
                    + context.escapeMini(entry.definitionState().name()) + "</dark_gray>");
        }

        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        sender.sendRichMessage("<dark_gray>Showing " + (end - start) + " of " + total + ". Page " + page + "/" + pages + ".</dark_gray>");
    }

    private void handleAdminCollisions(CommandSender sender, String[] args) {
        context.require(sender, "divinemarketplace.admin.collisions.view");
        int page = parsePage(sender, args, 2);
        if (page < 1) {
            return;
        }

        int pageSize = 8;
        int total = context.collisionLogService.countEntries();
        List<String> lines = context.collisionLogService.readPageNewestFirst(page - 1, pageSize);
        sender.sendRichMessage("<gold>Custom Item Collisions</gold> <gray>page " + page + "</gray>");
        if (lines.isEmpty()) {
            sender.sendRichMessage("<gray>No custom item collisions logged.</gray>");
            sender.sendRichMessage("<dark_gray>Log file:</dark_gray> <gray>" + context.escapeMini(context.collisionLogService.logFile().toAbsolutePath().toString()) + "</gray>");
            return;
        }
        for (String line : lines) {
            sender.sendRichMessage("<gray>-</gray> <white>" + context.escapeMini(context.shorten(line, 240)) + "</white>");
        }
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        sender.sendRichMessage("<dark_gray>Showing " + lines.size() + " of " + total + ". Page " + page + "/" + pages + ". Log: "
                + context.escapeMini(context.collisionLogService.logFile().toAbsolutePath().toString()) + "</dark_gray>");
    }

    private int parsePage(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException exception) {
            sender.sendRichMessage("<red>Invalid page number.</red>");
            return -1;
        }
    }
}
