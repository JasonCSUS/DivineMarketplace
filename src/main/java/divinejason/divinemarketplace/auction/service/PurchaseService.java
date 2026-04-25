package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.PurchaseResult;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles listing purchase flow.
 */
public interface PurchaseService {
    PurchaseResult purchase(Player buyer, UUID listingId, int quantity);
}
