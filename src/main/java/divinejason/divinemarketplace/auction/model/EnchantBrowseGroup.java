package divinejason.divinemarketplace.auction.model;


/*
 * File role: Enumerates enchant browse group values used by marketplace services, persistence, commands, and GUI rendering.
 */
import java.util.Locale;

/**
 * UI-only browse targets for enchanted books.
 *
 * This is NOT market identity.
 * A book can belong to multiple groups at once.
 *
 * Example:
 * - Sharpness V + Protection IV appears in both SWORD and ARMOR browse views
 * - It is still one listing with one market/display identity
 */
public enum EnchantBrowseGroup {
    SWORD,
    AXE,
    BOW,
    CROSSBOW,
    TRIDENT,
    ARMOR,
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    TOOLS,
    PICKAXE,
    SHOVEL,
    HOE,
    ELYTRA,
    SHIELD,
    SHEARS,
    UNIVERSAL,
    UNKNOWN;

    public String commandToken() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        String[] parts = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? name() : builder.toString();
    }

    public static EnchantBrowseGroup fromCommandToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return UNKNOWN;
        }
        String token = rawToken.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (token) {
            case "sword", "swords" -> SWORD;
            case "axe", "axes" -> AXE;
            case "bow", "bows" -> BOW;
            case "crossbow", "crossbows" -> CROSSBOW;
            case "trident", "tridents" -> TRIDENT;
            case "armor", "armour" -> ARMOR;
            case "helmet", "helm", "head", "helmets" -> HELMET;
            case "chest", "chestplate", "chestplates" -> CHESTPLATE;
            case "legs", "leggings", "pants" -> LEGGINGS;
            case "boot", "boots" -> BOOTS;
            case "tool", "tools" -> TOOLS;
            case "pickaxe", "pick", "pickaxes" -> PICKAXE;
            case "shovel", "spade", "shovels" -> SHOVEL;
            case "hoe", "hoes" -> HOE;
            case "elytra" -> ELYTRA;
            case "shield", "shields" -> SHIELD;
            case "shear", "shears" -> SHEARS;
            case "universal", "all", "any" -> UNIVERSAL;
            default -> UNKNOWN;
        };
    }
}
