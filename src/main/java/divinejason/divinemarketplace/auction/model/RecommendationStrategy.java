package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates recommendation strategy values used by marketplace services, persistence, commands, and GUI rendering.
 */
/**
 * How the UI should obtain a displayed recommendation for a listing/item.
 */
public enum RecommendationStrategy {
    /**
     * Use the item's own market profile / standard recommendation pipeline.
     */
    NORMAL_MODEL,

    /**
     * Used for mixed enchanted books.
     *
     * UI should inspect the component enchants and use the highest known single
     * enchant recommendation as the displayed recommended price.
     */
    PROXY_HIGHEST_COMPONENT,

    /**
     * Used when no recommendation should be shown, e.g. filled shulker package
     * listings.
     */
    NONE
}
