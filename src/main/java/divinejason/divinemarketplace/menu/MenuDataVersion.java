package divinejason.divinemarketplace.menu;

/*
 * File role: Monotonically-increasing counters that allow MenuPageCache to detect stale entries.
 */

import java.util.concurrent.atomic.AtomicLong;

/**
 * One AtomicLong per DataDomain. Callers increment the relevant counter after
 * every mutation; the renderer snapshots all counters before building a page and
 * stores the snapshot with the cached result so staleness can be checked cheaply.
 */
public final class MenuDataVersion {

    private final AtomicLong listings     = new AtomicLong(1);
    private final AtomicLong claims       = new AtomicLong(1);
    private final AtomicLong salesHistory = new AtomicLong(1);
    private final AtomicLong categories   = new AtomicLong(1);
    private final AtomicLong prices       = new AtomicLong(1);
    private final AtomicLong menuConfig   = new AtomicLong(1);

    public void markListingsChanged()     { listings.incrementAndGet(); }
    public void markClaimsChanged()       { claims.incrementAndGet(); }
    public void markSalesHistoryChanged() { salesHistory.incrementAndGet(); }
    public void markCategoriesChanged()   { categories.incrementAndGet(); }
    public void markPricesChanged()       { prices.incrementAndGet(); }
    public void markMenuConfigChanged()   { menuConfig.incrementAndGet(); }

    /** Returns an immutable snapshot of all current counter values. */
    public MenuDataVersionSnapshot snapshot() {
        return new MenuDataVersionSnapshot(
                listings.get(), claims.get(), salesHistory.get(), categories.get(), prices.get(), menuConfig.get());
    }
}
