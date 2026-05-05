package divinejason.divinemarketplace.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

interface MarketAdminCommandHandler {
    boolean handlesRoot(String root);

    void execute(CommandSender sender, String[] args);

    default Collection<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }

    default Collection<String> rootSuggestions(CommandSender sender) {
        return List.of();
    }
}
