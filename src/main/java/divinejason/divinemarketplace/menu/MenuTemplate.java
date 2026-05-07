package divinejason.divinemarketplace.menu;

/*
 * File role: Describes the fixed layout of one menu view — which slots are content vs filler.
 */

import java.util.Set;

/**
 * Per-view layout descriptor.
 *
 * <ul>
 *   <li>{@code contentMask} — {@code true} for every slot that may hold a non-filler item.
 *   <li>{@code contentSlots} — derived array of the true indices in contentMask.
 *   <li>{@code watchedDomains} — data domains that, when mutated, invalidate cached pages.
 *   <li>{@code cacheable} — whether this view's rendered output can be shared across players.
 * </ul>
 */
public final class MenuTemplate {

    private static final MenuTemplate EMPTY = new MenuTemplate(new boolean[54], Set.of(), false, 0);

    private final boolean[] contentMask;
    private final int[] contentSlots;
    private final Set<DataDomain> watchedDomains;
    private final boolean cacheable;
    private final int pageSize;

    public MenuTemplate(boolean[] contentMask, Set<DataDomain> watchedDomains, boolean cacheable) {
        this(contentMask, watchedDomains, cacheable, countMarked(contentMask));
    }

    public MenuTemplate(boolean[] contentMask, Set<DataDomain> watchedDomains, boolean cacheable, int pageSize) {
        this.contentMask = contentMask.clone();
        this.watchedDomains = Set.copyOf(watchedDomains);
        this.cacheable = cacheable;
        this.pageSize = Math.max(0, pageSize);

        int count = 0;
        for (boolean b : contentMask) {
            if (b) count++;
        }
        contentSlots = new int[count];
        int idx = 0;
        for (int i = 0; i < contentMask.length; i++) {
            if (contentMask[i]) contentSlots[idx++] = i;
        }
    }

    public static MenuTemplate empty() {
        return EMPTY;
    }

    public boolean isContentSlot(int slot) {
        return slot >= 0 && slot < contentMask.length && contentMask[slot];
    }

    /** Returns a defensive copy of the content slot indices. */
    public int[] contentSlots()             { return contentSlots.clone(); }
    public Set<DataDomain> watchedDomains() { return watchedDomains; }
    public boolean cacheable()              { return cacheable; }
    /** Number of data items per page for this view (0 for non-paginated views). */
    public int pageSize()                   { return pageSize; }

    private static int countMarked(boolean[] mask) {
        int count = 0;
        for (boolean b : mask) {
            if (b) count++;
        }
        return count;
    }
}
