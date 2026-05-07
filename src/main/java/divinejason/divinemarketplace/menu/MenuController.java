package divinejason.divinemarketplace.menu;

/*
 * Layer : gui/controller
 * Owns  : GUI opening/reopening, navigation-stack updates, action-map storage,
 *         and async page-preparation handoff for cacheable views.
 * Calls : MenuRenderer, MenuPagePreparationService, MenuSessionManager.
 * Avoids: market mutations, SQL, business rules.
 */

import divinejason.divinemarketplace.util.PerfTimer;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central open/refresh helper.
 *
 * <p>Heavy globally-cacheable views can prepare their plain {@code PageModel}
 * data asynchronously. The final Bukkit Inventory render/open step always runs
 * on the main server thread.</p>
 */
public final class MenuController {
    private final JavaPlugin plugin;
    private final MenuSessionManager sessionManager;
    private final MenuDataVersion dataVersion;
    private final MenuPagePreparationService pagePreparationService;
    private final MenuInvalidationService invalidationService;
    private MenuRenderer renderer;

    public MenuController(JavaPlugin plugin,
                          MenuSessionManager sessionManager,
                          MenuRenderer renderer,
                          MenuDataVersion dataVersion,
                          MenuPagePreparationService pagePreparationService,
                          MenuInvalidationService invalidationService) {
        this.plugin                 = Objects.requireNonNull(plugin, "plugin");
        this.sessionManager         = Objects.requireNonNull(sessionManager, "sessionManager");
        this.renderer               = Objects.requireNonNull(renderer, "renderer");
        this.dataVersion            = Objects.requireNonNull(dataVersion, "dataVersion");
        this.pagePreparationService = Objects.requireNonNull(pagePreparationService, "pagePreparationService");
        this.invalidationService    = Objects.requireNonNull(invalidationService, "invalidationService");
    }

    public void updateRenderer(MenuRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        invalidationService.markMenuConfigChanged();
        sessionManager.clearAllActions();
    }

    /** Returns the data-version tracker for legacy callers. Prefer invalidation(). */
    public MenuDataVersion dataVersion() {
        return dataVersion;
    }

    /** Returns the centralized menu invalidation gateway. */
    public MenuInvalidationService invalidation() {
        return invalidationService;
    }

    public void open(Player player, MenuSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");

        sessionManager.save(session);

        boolean perf = PerfTimer.enabled();
        MenuTemplate template = renderer.template(session);
        String cacheKey = PageModelBuilder.cacheKey(session);
        if (renderer.canPrepareAsync(session)) {
            MenuPagePreparationService.PreparedPage cached = pagePreparationService.getCached(cacheKey, template);
            if (cached != null) {
                long t = perf ? System.nanoTime() : 0;
                openPrepared(player, session, cached);
                if (perf) plugin.getLogger().info("[DivineMarketplace][perf] menu " + session.currentView().name()
                        + " cache=hit render=" + (System.nanoTime() - t) / 1_000_000 + "ms");
                return;
            }

            openLoading(player, session);
            if (perf) plugin.getLogger().info("[DivineMarketplace][perf] menu " + session.currentView().name() + " cache=miss");
            schedulePreparedOpen(player.getUniqueId(), session, template, cacheKey);
            return;
        }

        long t = perf ? System.nanoTime() : 0;
        MenuRenderResult renderResult = renderer.render(player, session);
        if (perf) plugin.getLogger().info("[DivineMarketplace][perf] menu " + session.currentView().name()
                + " cache=sync render=" + (System.nanoTime() - t) / 1_000_000 + "ms");
        openRendered(player, renderResult);
    }

    public void refresh(Player player) {
        MenuSession session = sessionManager.getOrCreate(player.getUniqueId()).withActionLocked(false);
        open(player, session);
    }

    private void openLoading(Player player, MenuSession session) {
        MenuRenderResult loading = renderer.renderLoading(session);
        sessionManager.saveActions(player.getUniqueId(), loading.actionsBySlot());
        player.openInventory(loading.inventory());
    }

    private void openPrepared(Player player, MenuSession session, MenuPagePreparationService.PreparedPage prepared) {
        MenuRenderResult renderResult = renderer.renderPrepared(player, session, prepared.model());
        openRendered(player, renderResult);
    }

    private void openRendered(Player player, MenuRenderResult renderResult) {
        sessionManager.saveActions(player.getUniqueId(), renderResult.actionsBySlot());
        player.openInventory(renderResult.inventory());
    }

    private boolean isStillViewingRequestedMenu(Player player, String requestedKey) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MarketMenuHolder holder)) {
            return false;
        }
        String visibleKey = holder.contextKey();
        return requestedKey.equals(visibleKey) || (requestedKey + ":loading").equals(visibleKey);
    }

    private void schedulePreparedOpen(UUID playerId, MenuSession requestedSession, MenuTemplate template, String requestedKey) {
        pagePreparationService.prepareAsync(requestedSession, template).whenComplete((prepared, throwable) ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    return;
                }

                MenuSession current = sessionManager.getOrCreate(playerId);
                String currentKey = PageModelBuilder.cacheKey(current);
                if (!requestedKey.equals(currentKey) || !isStillViewingRequestedMenu(player, requestedKey)) {
                    return; // Player navigated away or closed the menu while the page was building.
                }

                if (throwable != null) {
                    player.sendRichMessage("<red>Market page failed to load.</red> <gray>Try again in a moment.</gray>");
                    MenuRenderResult fallback = renderer.render(player, current);
                    openRendered(player, fallback);
                    return;
                }

                MenuTemplate currentTemplate = renderer.template(current);
                if (!pagePreparationService.isFresh(prepared, currentTemplate)) {
                    open(player, current);
                    return;
                }

                openPrepared(player, current, prepared);
            })
        );
    }
}
