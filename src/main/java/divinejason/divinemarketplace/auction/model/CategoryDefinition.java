package divinejason.divinemarketplace.auction.model;


/*
 * File role: Carries immutable category definition data between marketplace services, persistence stores, commands, and GUI rendering.
 */
/**
 * Config-defined top-level browse category.
 *
 * Current design:
 * - categories are always shown in the top-level categories menu
 * - categories are loaded in order of appearance from config
 * - empty categories stay visible and show listing count lore such as "0"
 * - slot placement is dynamic and handled by the menu layer, not stored here
 *
 * iconKey should stay flexible enough for:
 * - vanilla materials like STONE or DIAMOND_SWORD
 * - custom/resource-pack icon ids such as itemsadder:some_icon when an icon resolver supports them
 */
public record CategoryDefinition(
        String id,
        String displayName,
        String iconKey
) {
}
