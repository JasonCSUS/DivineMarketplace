package divinejason.divinemarketplace.menu;

/*
 * File role: LRU cache of pre-built PageModel objects for globally-shared views.
 */

import divinejason.divinemarketplace.menu.model.PageModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Caches {@link PageModel} snapshots keyed by the session context string, validated
 * against a {@link MenuDataVersionSnapshot} before use.
 *
 * <p>Storing plain data models (no Bukkit objects) means:</p>
 * <ul>
 *   <li>The cache survives visual-config reloads — the same model can be re-rendered
 *       with a new {@link MenuItemFactory} after a menu reload.</li>
 *   <li>Entries are lighter than ItemStack maps and can be shared across players.</li>
 * </ul>
 *
 * <p>Thread safety: all mutating methods are {@code synchronized}.  GUI renders run
 * on the main thread; {@link #invalidateAll()} may be called from an async reload thread.</p>
 */
public final class MenuPageCache {

    private static final int MAX_ENTRIES = 256;

    public record CachedEntry(PageModel model, MenuDataVersionSnapshot snapshot) {}

    private final LinkedHashMap<String, CachedEntry> lru;

    public MenuPageCache() {
        this.lru = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedEntry> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    /**
     * Returns the cached model if present and still fresh, otherwise removes the
     * stale entry and returns {@code null}.
     */
    public synchronized PageModel get(String key,
                                      MenuDataVersionSnapshot current,
                                      Set<DataDomain> watchedDomains) {
        CachedEntry entry = lru.get(key);
        if (entry == null) return null;
        if (!entry.snapshot().freshFor(current, watchedDomains)) {
            lru.remove(key);
            return null;
        }
        return entry.model();
    }

    /** Stores a page model with its version snapshot. */
    public synchronized void put(String key, PageModel model, MenuDataVersionSnapshot snapshot) {
        lru.put(key, new CachedEntry(model, snapshot));
    }

    /** Drops all cached models. Called on visual-config reload. */
    public synchronized void invalidateAll() {
        lru.clear();
    }
}
