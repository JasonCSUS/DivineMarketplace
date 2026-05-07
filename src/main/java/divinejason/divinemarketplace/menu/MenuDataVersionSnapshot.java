package divinejason.divinemarketplace.menu;

/*
 * File role: Immutable snapshot of MenuDataVersion counters stored alongside each cached page.
 */

import java.util.Set;

/**
 * Captured counter values at render time. A cached page is fresh as long as
 * every domain it watches matches the current live counter.
 */
public record MenuDataVersionSnapshot(
        long listings,
        long claims,
        long salesHistory,
        long categories,
        long prices,
        long menuConfig
) {
    /**
     * Returns true when this snapshot still matches {@code current} for every
     * domain in {@code watchedDomains}.
     */
    public boolean freshFor(MenuDataVersionSnapshot current, Set<DataDomain> watchedDomains) {
        for (DataDomain domain : watchedDomains) {
            switch (domain) {
                case LISTINGS      -> { if (listings     != current.listings)     return false; }
                case CLAIMS        -> { if (claims       != current.claims)       return false; }
                case SALES_HISTORY -> { if (salesHistory != current.salesHistory) return false; }
                case CATEGORIES    -> { if (categories   != current.categories)   return false; }
                case PRICES        -> { if (prices       != current.prices)       return false; }
                case MENU_CONFIG   -> { if (menuConfig   != current.menuConfig)   return false; }
            }
        }
        return true;
    }
}
