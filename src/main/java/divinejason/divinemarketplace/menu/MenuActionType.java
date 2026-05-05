package divinejason.divinemarketplace.menu;

/**
 * Stable click action ids captured at render time.
 *
 * Renderers decide which slot owns which action. The click router consumes the
 * action map instead of recomputing page math from raw slot numbers.
 */
public enum MenuActionType {
    NONE,
    BACK,
    PREVIOUS_PAGE,
    NEXT_PAGE,
    OPEN_VIEW,
    OPEN_CATEGORY,
    OPEN_MARKET_GROUP,
    OPEN_ENCHANT_TARGET,
    OPEN_LISTING,
    OPEN_CLAIM,
    START_LISTING_PROMPT,
    START_RELIST_PROMPT,
    START_SEARCH_PROMPT,
    CLAIM_ONE_CHUNK,
    CLAIM_AS_MUCH_AS_FITS,
    CLAIM_EARNINGS,
    SORT_CYCLE,
    QUANTITY_DECREASE,
    QUANTITY_INCREASE,
    CONFIRM_PURCHASE,
    CANCEL_LISTING,
    OPEN_SALE_HISTORY,
    OPEN_PRICE_HISTORY,
    PREVIOUS_PRICE_HISTORY_MONTH,
    NEXT_PRICE_HISTORY_MONTH
}
