package divinejason.divinemarketplace.auction.persistence;

import java.util.UUID;

/**
 * Primary binary storage for per-player pending money balances.
 *
 * File target:
 * - data/money_claims.bin
 *
 * Locked rules:
 * - one running balance per owner UUID
 * - missing record means zero balance
 * - zero balances should be deleted rather than stored
 * - internal money uses integer hundredths
 */
public interface BinaryMoneyClaimStore {

    long getBalanceOrZero(UUID ownerUuid);

    void addToBalance(UUID ownerUuid, long amountToAdd);

    /**
     * Subtract the exact claimed amount after a successful payout.
     *
     * This is safer than blindly setting balance to zero.
     */
    void subtractFromBalance(UUID ownerUuid, long amountToSubtract);

    void deleteIfZero(UUID ownerUuid);

    boolean hasBalance(UUID ownerUuid);
}
