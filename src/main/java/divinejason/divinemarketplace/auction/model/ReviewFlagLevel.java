package divinejason.divinemarketplace.auction.model;

/**
 * Administrative attention level for an item or definition.
 *
 * NONE
 * - no review needed
 *
 * NORMAL
 * - newly discovered but still safe enough for normal listing and grouping
 * - usually needs category cleanup or simple admin confirmation
 *
 * HIGH_PRIORITY
 * - identity is ambiguous, unsafe, or otherwise needs developer/admin attention
 * - item may be allowed to list but should stay out of sorted browsing until fixed
 */
public enum ReviewFlagLevel {
    NONE,
    NORMAL,
    HIGH_PRIORITY
}
