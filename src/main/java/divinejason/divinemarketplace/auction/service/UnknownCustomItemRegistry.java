package divinejason.divinemarketplace.auction.service;


/*
 * File role: Defines the service contract for unknown custom item registry so command, GUI, and runtime code share one behavior boundary.
 */
import divinejason.divinemarketplace.auction.model.UnknownCustomItemRecord;

/**
 * Tracks newly discovered or unresolved custom items for admin review.
 *
 * Rules locked so far:
 * - all newly discovered custom items should be flagged for review so admins can
 *   sort them from readable logs and admin commands
 * - newly discovered but otherwise safe items should generally be NORMAL review
 * - ambiguous or system-risk items should be HIGH_PRIORITY review
 * - alerts should happen once per unique issue signature, not spam repeatedly
 * - servers that do not care about unresolved custom sorting should be able to
 *   ignore the warnings and continue using the marketplace
 */
public interface UnknownCustomItemRegistry {
    UnknownCustomItemRecord findByItemType(String itemType);
}
