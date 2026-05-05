package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates browse visibility values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * Controls where a listing/group may appear in player-facing browsing.
 *
 * FULLY_SORTED
 * - eligible for top-level category -> subcategory -> sorted listing flows
 *
 * RECENT_ONLY
 * - still listable and buyable, but only shown in Recent Listings, seller views,
 *   and admin/debug views because the item could not be safely grouped
 */
public enum BrowseVisibility {
    FULLY_SORTED,
    RECENT_ONLY
}
