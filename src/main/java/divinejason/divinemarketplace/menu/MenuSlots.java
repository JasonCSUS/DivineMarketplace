package divinejason.divinemarketplace.menu;


/*
 * File role: Defines fallback GUI slot numbers used when menu.yml is missing or invalid.
 */
/**
 * Central slot constants so renderers and click routing stay aligned.
 *
 * Exact filler colors/materials may still change during Paper implementation,
 * but the rough storyboard slot map is locked enough to centralize here now.
 */
public final class MenuSlots {
    private MenuSlots() {}

    public static final int BACK = 0;
    public static final int TOP_RIGHT = 8;
    public static final int PREVIOUS_PAGE = 45;
    public static final int NEXT_PAGE = 53;

    public static final int MAIN_MY_LISTINGS = 0;
    public static final int MAIN_CLAIMS = 9;
    public static final int MAIN_CLAIM_EARNINGS = 18;
    public static final int MAIN_LIST_HELD_ITEM = 4;
    public static final int MAIN_SEARCH = 8;
    public static final int MAIN_ALL_LISTINGS = 49;

    public static final int ITEM_DETAIL_DECREASE = 21;
    public static final int ITEM_DETAIL_QUANTITY = 22;
    public static final int ITEM_DETAIL_INCREASE = 23;
    public static final int ITEM_DETAIL_CONFIRM = 31;
    public static final int ITEM_DETAIL_CANCEL = 40;
    public static final int ITEM_DETAIL_SALE_HISTORY = 37;
    public static final int ITEM_DETAIL_PRICE_HISTORY = 43;
    public static final int CLAIM_DETAIL_RELIST = 22;
    public static final int CLAIM_DETAIL_ONE_CHUNK = 31;
    public static final int CLAIM_DETAIL_AS_MUCH_AS_FITS = 40;
    public static final int CLAIMS_EARNINGS = 8;
    public static final int PRICE_HISTORY_MONTH_CONTEXT = 4;
    public static final int PRICE_HISTORY_PREVIOUS_MONTH = 3;
    public static final int PRICE_HISTORY_NEXT_MONTH = 5;

    public static final int[] MAIN_CATEGORY_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };

    public static final int[] CATEGORY_BROWSER_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33
    };

    public static final int[] LISTING_BROWSER_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };

    public static final int[] CLAIM_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };

    public static final int[] HISTORY_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24,
            29, 30, 31, 32, 33,
            38, 39, 40, 41, 42
    };
}
