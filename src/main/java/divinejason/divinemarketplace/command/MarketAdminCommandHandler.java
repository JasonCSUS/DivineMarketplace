package divinejason.divinemarketplace.command;


/*
 * File role: Defines the command-handler contract for market admin command routing.
 */
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;

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
