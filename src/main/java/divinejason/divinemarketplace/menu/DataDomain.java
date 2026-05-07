package divinejason.divinemarketplace.menu;

/*
 * File role: Identifies which in-memory data domains affect cached menu pages.
 */

/** Logical data domains whose version counters drive page-cache invalidation. */
public enum DataDomain {
    LISTINGS,
    CLAIMS,
    SALES_HISTORY,
    CATEGORIES,
    PRICES,
    MENU_CONFIG
}
