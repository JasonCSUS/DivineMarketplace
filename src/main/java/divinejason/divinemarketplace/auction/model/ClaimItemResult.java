package divinejason.divinemarketplace.auction.model;

import java.util.UUID;

/**
 * Result object for one item-claim redemption attempt.
 *
 * The service returns this instead of forcing GUI/command callers to infer
 * outcome from thrown exceptions. Player-facing code can report the exact item
 * name, amount delivered, and amount left in claim storage while still keeping
 * claim persistence rules inside ClaimService.
 */
public record ClaimItemResult(
        boolean success,
        UUID claimId,
        String marketDisplayName,
        int requestedAmount,
        int claimedAmount,
        int remainingAmount,
        String failureMessage
) {
    public static ClaimItemResult success(
            UUID claimId,
            String marketDisplayName,
            int requestedAmount,
            int claimedAmount,
            int remainingAmount
    ) {
        return new ClaimItemResult(
                true,
                claimId,
                marketDisplayName,
                Math.max(0, requestedAmount),
                Math.max(0, claimedAmount),
                Math.max(0, remainingAmount),
                null
        );
    }

    public static ClaimItemResult failure(UUID claimId, String marketDisplayName, int requestedAmount, String failureMessage) {
        return new ClaimItemResult(
                false,
                claimId,
                marketDisplayName,
                Math.max(0, requestedAmount),
                0,
                0,
                failureMessage == null || failureMessage.isBlank() ? "Claim could not be processed." : failureMessage
        );
    }

    public boolean claimRemoved() {
        return success && remainingAmount <= 0;
    }
}
