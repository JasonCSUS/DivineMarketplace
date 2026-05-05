package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

/**
 * Mandatory SQLite-backed admin/audit transaction record.
 *
 * This is separate from:
 * - player-facing market history
 * - recommendation training inputs
 * - human-readable text log copies
 *
 * All market actions should be written to SQLite admin history so admins can
 * inspect disputes, accidents, exploit attempts, and system errors.
 */
public record AdminTransactionRecord(
        String transactionId,
        long timestampEpochMillis,
        AdminTransactionType transactionType,
        String listingId,
        UUID sellerUuid,
        UUID buyerUuid,
        UUID ownerUuid,
        String marketKey,
        String marketDisplayName,
        String categoryId,
        String itemSummary,
        int amount,
        long totalPrice,
        long unitPrice,
        String status,
        String reason
) {
}
