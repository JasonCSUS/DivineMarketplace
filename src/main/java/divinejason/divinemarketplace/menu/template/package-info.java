/**
 * Reusable GUI chrome chunks applied to Bukkit inventories before screen-specific content.
 *
 * <p>Each {@link divinejason.divinemarketplace.menu.template.MenuSubTemplate} handles one
 * structural concern: border fill, navigation arrows, confirm/cancel action clusters, player
 * summary headers, and admin overlays. {@link divinejason.divinemarketplace.menu.MenuRenderer}
 * selects and applies the right templates so individual render methods stay focused on content
 * layout rather than chrome boilerplate.</p>
 *
 * <p>Templates that carry no dynamic state ({@code BorderTemplate.WITH_BACK},
 * {@code AdminControlsTemplate.NONE}) are pre-built static instances and are never re-allocated
 * between renders. Templates that carry per-render data ({@code PaginationFooterTemplate},
 * {@code ConfirmCancelTemplate}, {@code PlayerSummaryHeaderTemplate}) are lightweight value
 * objects created once per render call.</p>
 */
package divinejason.divinemarketplace.menu.template;
