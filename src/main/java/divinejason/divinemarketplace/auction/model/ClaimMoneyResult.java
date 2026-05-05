package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable claim money result data between marketplace services, persistence stores, commands, and GUI rendering.
 */
/**
 * Result object for Vault-backed seller earnings payout.
 *
 * Amounts are stored as integer hundredths to match the rest of the market
 * economy layer. Commands and GUI code format the number for players instead
 * of re-reading claim storage and guessing whether anything happened.
 */
public record ClaimMoneyResult(
        boolean success,
        long claimedAmount,
        String failureMessage
) {
    public static ClaimMoneyResult success(long claimedAmount) {
        return new ClaimMoneyResult(true, Math.max(0L, claimedAmount), null);
    }

    public static ClaimMoneyResult failure(long attemptedAmount, String failureMessage) {
        return new ClaimMoneyResult(
                false,
                Math.max(0L, attemptedAmount),
                failureMessage == null || failureMessage.isBlank() ? "Earnings could not be claimed." : failureMessage
        );
    }

    public boolean empty() {
        return success && claimedAmount <= 0L;
    }
}
