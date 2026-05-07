package divinejason.divinemarketplace.auction.service.purchase;


/*
 * File role: Defines the service contract for purchase service so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.PurchaseResult;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Handles listing purchase flow.
 */
public interface PurchaseService {
    PurchaseResult purchase(Player buyer, UUID listingId, int quantity);
}
