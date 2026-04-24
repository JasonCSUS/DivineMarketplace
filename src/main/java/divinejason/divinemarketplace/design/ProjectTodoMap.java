package divinejason.divinemarketplace.design;

/**
 * Non-runtime planning anchor so the package tree shows up in the project.
 *
 * Current architecture snapshot:
 * - custom items prefer ItemAdder/custom item id when readable
 * - fallback custom discovery uses material + custom model data
 * - new custom items can be auto-defined into category "unsorted" using their
 *   discovered material/model/display data
 * - all newly discovered custom items should get NORMAL admin review
 * - unresolved unsafe items should still be listable but only appear in Recent
 *   Listings and admin/seller views (RECENT_ONLY + HIGH_PRIORITY)
 * - top-level categories are config-defined and always shown with live counts
 * - subcategories are dynamic and only appear when active listings exist
 * - subcategory preview icons should prefer cloned/discovered item previews so
 *   unknown custom items still render correctly before manual sorting
 * - mixed enchanted books proxy their displayed recommendation from the highest-
 *   valued sub-enchant and do not participate in player-facing market history
 *   or recommendation training
 * - admin/audit transaction logging is separate from player-facing market data
 * - admin alerts should happen once per unique issue signature without spam
 */
public final class ProjectTodoMap {
    private ProjectTodoMap() {
    }
}
