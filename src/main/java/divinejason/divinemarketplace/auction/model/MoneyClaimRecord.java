package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

/**
 * Durable money-claim balance stored in money_claims.bin.
 *
 * Locked v1 rules:
 * - one running balance per player
 * - sale proceeds add to amount
 * - /market claim earnings pays the stored amount through Vault
 * - successful payout drains amount to 0 and deletes the record
 * - missing record means zero balance
 * - all admin traceability lives in audit history, not in this record
 *
 * Money values should be stored as integer hundredths internally.
 */
public record MoneyClaimRecord(
        UUID ownerUuid,
        long amount
) {
}
