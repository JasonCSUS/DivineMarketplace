package divinejason.divinemarketplace.menu;

/*
 * Layer : gui/async
 * Owns  : Async preparation of cacheable plain PageModel data.
 * Calls : PageModelBuilder, MenuPageCache, MenuDataVersion, Bukkit scheduler.
 * Avoids: Bukkit Inventory creation, ItemStack rendering, Player/Inventory access.
 *
 * Cacheable menu pages are built from memory-first marketplace data and contain
 * no Bukkit objects. This service keeps those heavier sort/filter/page builds
 * off the main server thread, then MenuController returns to the main thread for
 * the final Bukkit inventory render/open step.
 */

import divinejason.divinemarketplace.menu.model.PageModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builds cacheable {@link PageModel} instances asynchronously and stores them in
 * {@link MenuPageCache}. In-flight builds are coalesced by cache key so several
 * players opening the same expensive page do not all rebuild it at once.
 */
public final class MenuPagePreparationService {

    /** A prepared plain page model plus the data-version snapshot it was based on. */
    public record PreparedPage(String cacheKey, PageModel model, MenuDataVersionSnapshot snapshot) {}

    private final JavaPlugin plugin;
    private final MenuDataVersion dataVersion;
    private final MenuPageCache pageCache;
    private final PageModelBuilder pageModelBuilder;
    private final Logger logger;
    private final boolean debugTimings;
    private final ConcurrentMap<String, CompletableFuture<PreparedPage>> inFlight = new ConcurrentHashMap<>();

    public MenuPagePreparationService(JavaPlugin plugin,
                                      MenuDataVersion dataVersion,
                                      MenuPageCache pageCache,
                                      PageModelBuilder pageModelBuilder) {
        this.plugin           = Objects.requireNonNull(plugin, "plugin");
        this.dataVersion      = Objects.requireNonNull(dataVersion, "dataVersion");
        this.pageCache        = Objects.requireNonNull(pageCache, "pageCache");
        this.pageModelBuilder = Objects.requireNonNull(pageModelBuilder, "pageModelBuilder");
        this.logger           = plugin.getLogger();
        this.debugTimings     = Boolean.getBoolean("divinemarketplace.gui.debug");
    }

    /** Returns true if this template can be prepared off-thread as plain data. */
    public boolean canPrepareAsync(MenuTemplate template) {
        return template != null && template.cacheable();
    }

    /** Returns a fresh cached model if one is available. */
    public PreparedPage getCached(String cacheKey, MenuTemplate template) {
        if (!canPrepareAsync(template)) {
            return null;
        }
        MenuDataVersionSnapshot current = dataVersion.snapshot();
        PageModel cached = pageCache.get(cacheKey, current, template.watchedDomains());
        if (cached != null && debugTimings) {
            logger.info("[GUI] page cache hit key=" + cacheKey + " domains=" + template.watchedDomains());
        }
        return cached == null ? null : new PreparedPage(cacheKey, cached, current);
    }

    /** Returns true when the prepared page is still valid for the template's watched data domains. */
    public boolean isFresh(PreparedPage prepared, MenuTemplate template) {
        if (prepared == null || template == null) {
            return false;
        }
        return prepared.snapshot().freshFor(dataVersion.snapshot(), template.watchedDomains());
    }

    /**
     * Builds or joins an in-flight async page preparation job.
     *
     * <p>The snapshot captured before the build is stored with the cache entry.
     * If market data changes while the build is running, that entry will be seen
     * as stale immediately and the controller can request a fresh build instead
     * of treating old page data as current.</p>
     */
    public CompletableFuture<PreparedPage> prepareAsync(MenuSession session, MenuTemplate template) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(template, "template");

        String cacheKey = PageModelBuilder.cacheKey(session);
        PreparedPage cached = getCached(cacheKey, template);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (debugTimings) {
            logger.info("[GUI] page cache miss key=" + cacheKey + " view=" + session.currentView());
        }

        return inFlight.computeIfAbsent(cacheKey, ignored -> {
            CompletableFuture<PreparedPage> future = new CompletableFuture<>();
            MenuSession snapshotSession = session;
            int pageSize = template.pageSize();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                long startNanos = System.nanoTime();
                try {
                    MenuDataVersionSnapshot before = dataVersion.snapshot();
                    PageModel built = pageModelBuilder.buildCacheable(snapshotSession, pageSize);
                    PreparedPage prepared = new PreparedPage(cacheKey, built, before);

                    pageCache.put(cacheKey, built, before);
                    if (debugTimings) {
                        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
                        logger.info("[GUI] async page build view=" + snapshotSession.currentView()
                                + " key=" + cacheKey + " ms=" + millis);
                    }
                    future.complete(prepared);
                } catch (Throwable throwable) {
                    logger.warning("[GUI] Async page preparation failed for " + snapshotSession.currentView()
                            + " key=" + cacheKey + ": " + throwable.getMessage());
                    future.completeExceptionally(throwable);
                } finally {
                    inFlight.remove(cacheKey);
                }
            });

            return future;
        });
    }

    /** Drops any completed cache entries and forgets in-flight jobs after menu config reload. */
    public void invalidateAll() {
        pageCache.invalidateAll();
        inFlight.clear();
    }
}
