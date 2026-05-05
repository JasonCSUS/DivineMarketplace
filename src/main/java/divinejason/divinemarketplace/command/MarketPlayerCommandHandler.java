package divinejason.divinemarketplace.command;

import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

interface MarketPlayerCommandHandler {
    boolean handlesRootToken(String rootToken);
    void execute(CommandSender sender, String[] args);
    default Collection<String> suggest(CommandSender sender, String[] args) {
        return List.of();
    }
}
