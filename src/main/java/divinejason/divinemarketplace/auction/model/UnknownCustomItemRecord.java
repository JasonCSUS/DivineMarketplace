package divinejason.divinemarketplace.auction.model;

import java.time.Instant;

import org.bukkit.Material;

/**
 * Tracks a newly discovered or unresolved custom item identity.
 *
 * Locked admin-review policy:
 * - all newly discovered custom items should be flagged for review so admins
 *   can categorize them more easily later
 * - safe new items are NORMAL review and may still be FULLY_SORTED, typically in
 *   category "unsorted"
 * - unsafe/ambiguous items are HIGH_PRIORITY and should become RECENT_ONLY while
 *   still remaining listable
 * - alerts should fire once per unique issue signature and then stay quiet
 */
public record UnknownCustomItemRecord(
        String itemType,
        Material sampleMaterial,
        Float sampleCustomModelData,
        String sampleMarketDisplayName,
        String categoryId,
        ReviewFlagLevel reviewFlagLevel,
        BrowseVisibility browseVisibility,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int timesSeen,
        boolean adminAlertSent
) {
}
