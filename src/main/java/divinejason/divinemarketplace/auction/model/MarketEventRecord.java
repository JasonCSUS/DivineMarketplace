package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable canonical market event data between marketplace services, persistence stores, commands, and GUI rendering.
 */
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * Canonical market event record stored in the market_events table.
 *
 * Replaces both SaleRecord (player-facing purchase history) and
 * AdminTransactionRecord (admin audit trail) as the single write target for all
 * market actions.
 *
 * Player history projection rule:
 * - BUY events where itemSnapshot != null appear in player-facing sale history.
 * - itemSnapshot is null for history-excluded items (admin audit only).
 *
 * Training projection rule:
 * - BUY events where trainingParticipation == INCLUDED feed the recommendation engine.
 * - trainingParticipation is null for non-BUY events and excluded BUY events.
 *
 * Admin projection rule:
 * - All event types at all times appear in admin history, no filtering.
 *
 * Money rule:
 * - totalPrice and unitPrice use integer hundredths internally.
 */
public record MarketEventRecord(
        String eventId,
        long timestampEpochMillis,
        MarketEventType eventType,
        String listingId,
        UUID sellerUuid,
        UUID buyerUuid,
        UUID ownerUuid,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        String itemSummary,
        ItemStack itemSnapshot,
        int amount,
        long totalPrice,
        long unitPrice,
        String status,
        String reason,
        MarketTrainingParticipation trainingParticipation
) {
}
