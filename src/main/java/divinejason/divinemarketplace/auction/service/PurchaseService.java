package divinejason.divinemarketplace.auction.service;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles listing purchase flow.
 *
 * Locked v1 behavior:
 * - menu UI is not source of truth
 * - re-fetch live listing before purchase
 * - quantity may be partial and must be between 1 and listing.amount
 * - unitPrice is authoritative; total price is derived
 * - create exact SaleRecord on each purchase event
 * - add seller proceeds to MoneyClaimRecord
 * - deliver item immediately if safe, otherwise create/merge ItemClaimRecord
 */
public interface PurchaseService {

    /**
     * Purchase a quantity from one active listing.
     *
     * PSEUDOCODE:
     * - load live listing
     * - verify listing still exists and quantity is available
     * - compute totalPrice = unitPrice * quantity
     * - verify buyer has enough money
     * - withdraw buyer money via Vault boundary conversion
     * - reduce listing amount
     * - if amount reaches 0 remove listing, else save updated listing
     * - create player-facing SaleRecord if history-visible
     * - create admin sale history record always
     * - add seller money claim
     * - attempt item delivery, otherwise create/merge item claim for buyer
     * - refresh category/index state if listing changed or vanished
     */
    void purchase(Player buyer, UUID listingId, int quantity);
}
